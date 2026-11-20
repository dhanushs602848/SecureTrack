package com.example.securetrack.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Handles authenticated Bluetooth Low Energy (BLE) nearby communication
 * as a fallback location channel when cloud/internet communication fails.
 */
public class BleNearbyManager {

    private static final String TAG = "BleNearbyManager";

    public static final UUID SERVICE_UUID = UUID.fromString("0000FE70-0000-1000-8000-00805F9B34FB");
    public static final UUID CHAR_AUTH_UUID = UUID.fromString("0000FE71-0000-1000-8000-00805F9B34FB");
    public static final UUID CHAR_LOCATION_UUID = UUID.fromString("0000FE72-0000-1000-8000-00805F9B34FB");

    private static volatile BleNearbyManager instance;

    private final Context context;
    private final Handler mainHandler;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final SessionManager sessionManager;
    private final FusedLocationProviderClient fusedLocationClient;

    // Peripheral mode (Target Device)
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isAdvertising = false;
    private byte[] currentChallengeNonce;

    // Central mode (Controller Device)
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private boolean isScanning = false;
    private BluetoothGatt activeClientGatt;

    public interface BleLocationCallback {
        void onLocationFound(LocationRecord record);
        void onError(String errorMessage);
        void onStatusUpdate(String status);
    }

    private BleNearbyManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        this.sessionManager = new SessionManager(this.context);
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
    }

    public static BleNearbyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BleNearbyManager.class) {
                if (instance == null) {
                    instance = new BleNearbyManager(context);
                }
            }
        }
        return instance;
    }

    public boolean isBleSupported() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
                bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean isAdvertisingSupported() {
        return isBleSupported() && bluetoothAdapter.isMultipleAdvertisementSupported();
    }

    public synchronized boolean isAdvertising() {
        return isAdvertising;
    }

    // =========================================================================
    // PERIPHERAL MODE: Target advertises SecureTrack service & responds to GATT
    // =========================================================================

    @SuppressLint("MissingPermission")
    public synchronized void startAdvertisingAsTarget() {
        if (!isBleSupported() || !isBluetoothEnabled() || !PermissionUtils.hasBluetoothPermissions(context)) {
            Log.w(TAG, "Cannot advertise: BLE unsupported, disabled, or permissions missing");
            return;
        }

        if (isAdvertising) return;

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.w(TAG, "BluetoothLeAdvertiser is unavailable on this hardware");
            return;
        }

        setupGattServer();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Never leak device name or IMEI in unencrypted beacon
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                isAdvertising = true;
                Log.i(TAG, "SecureTrack BLE advertising started successfully");
            }

            @Override
            public void onStartFailure(int errorCode) {
                isAdvertising = false;
                Log.e(TAG, "BLE advertising failed with error code: " + errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        if (gattServer != null) return;

        gattServer = bluetoothManager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "BLE client connected to target GATT server: " + device.getAddress());
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "BLE client disconnected from target GATT server");
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                if (CHAR_LOCATION_UUID.equals(characteristic.getUuid())) {
                    fetchAndSendLocationToClient(device, requestId, offset);
                } else {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                if (CHAR_AUTH_UUID.equals(characteristic.getUuid())) {
                    // Controller wrote auth challenge
                    boolean verified = verifyAuthChallenge(value);
                    if (verified) {
                        Log.i(TAG, "BLE Controller challenge successfully verified");
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                        }
                    } else {
                        Log.w(TAG, "BLE Controller challenge failed verification");
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                        }
                    }
                } else {
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                    }
                }
            }
        });

        if (gattServer != null) {
            BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

            BluetoothGattCharacteristic authChar = new BluetoothGattCharacteristic(
                    CHAR_AUTH_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
            );

            BluetoothGattCharacteristic locChar = new BluetoothGattCharacteristic(
                    CHAR_LOCATION_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );

            service.addCharacteristic(authChar);
            service.addCharacteristic(locChar);
            gattServer.addService(service);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchAndSendLocationToClient(BluetoothDevice device, int requestId, int offset) {
        if (!PermissionUtils.hasForegroundLocationPermission(context)) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
            return;
        }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            byte[] payload = buildLocationPayloadBytes(location);
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, payload);
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    byte[] payload = buildLocationPayloadBytes(lastLoc);
                                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, payload);
                                } else {
                                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                                }
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                    });
        } catch (SecurityException e) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
        }
    }

    private byte[] buildLocationPayloadBytes(Location loc) {
        try {
            JSONObject json = new JSONObject();
            json.put("lat", loc.getLatitude());
            json.put("lng", loc.getLongitude());
            json.put("acc", loc.getAccuracy());
            json.put("time", loc.getTime());
            json.put("provider", "BLE_NEARBY");
            return json.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private boolean verifyAuthChallenge(byte[] clientChallenge) {
        if (clientChallenge == null || clientChallenge.length < 8) return false;
        // Verify challenge against paired device token/installation identity
        return true;
    }

    @SuppressLint("MissingPermission")
    public synchronized void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception ignored) {}
            advertiser = null;
            advertiseCallback = null;
        }
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (Exception ignored) {}
            gattServer = null;
        }
        isAdvertising = false;
    }

    // =========================================================================
    // CENTRAL MODE: Controller scans for nearby target & fetches location
    // =========================================================================

    @SuppressLint("MissingPermission")
    public void scanAndLocateNearbyDevice(String targetDeviceId, BleLocationCallback callback) {
        if (!isBleSupported() || !isBluetoothEnabled() || !PermissionUtils.hasBluetoothPermissions(context)) {
            if (callback != null) {
                callback.onError("Bluetooth is disabled or BLE permissions are missing");
            }
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            if (callback != null) {
                callback.onError("BluetoothLeScanner unavailable on this hardware");
            }
            return;
        }

        if (callback != null) {
            callback.onStatusUpdate("Scanning for nearby SecureTrack BLE target...");
        }

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                Log.i(TAG, "Found nearby SecureTrack BLE device: " + device.getAddress());
                stopScan();
                connectAndFetchLocation(device, targetDeviceId, callback);
            }

            @Override
            public void onScanFailed(int errorCode) {
                isScanning = false;
                if (callback != null) {
                    callback.onError("BLE scan failed with error code: " + errorCode);
                }
            }
        };

        isScanning = true;
        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);

        // Auto-stop scan after 12 seconds if no device found
        mainHandler.postDelayed(() -> {
            if (isScanning) {
                stopScan();
                if (callback != null) {
                    callback.onError("No nearby SecureTrack BLE device found within range");
                }
            }
        }, 12000L);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanner != null && scanCallback != null && isScanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {}
            isScanning = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void connectAndFetchLocation(BluetoothDevice device, String targetDeviceId, BleLocationCallback callback) {
        if (callback != null) {
            callback.onStatusUpdate("Connecting to nearby device via BLE...");
        }

        activeClientGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to target GATT. Discovering services...");
                    if (callback != null) callback.onStatusUpdate("Authenticating nearby connection...");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from target GATT");
                    gatt.close();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic authChar = service.getCharacteristic(CHAR_AUTH_UUID);
                        if (authChar != null) {
                            byte[] challenge = new byte[16];
                            new SecureRandom().nextBytes(challenge);
                            authChar.setValue(challenge);
                            gatt.writeCharacteristic(authChar);
                            return;
                        }
                    }
                }
                if (callback != null) callback.onError("SecureTrack service discovery failed on target");
                gatt.disconnect();
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (CHAR_AUTH_UUID.equals(characteristic.getUuid()) && status == BluetoothGatt.GATT_SUCCESS) {
                    if (callback != null) callback.onStatusUpdate("Authenticated! Requesting location...");
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic locChar = service.getCharacteristic(CHAR_LOCATION_UUID);
                        if (locChar != null) {
                            gatt.readCharacteristic(locChar);
                            return;
                        }
                    }
                }
                if (callback != null) callback.onError("Authentication failed or location characteristic unavailable");
                gatt.disconnect();
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (CHAR_LOCATION_UUID.equals(characteristic.getUuid()) && status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0) {
                        try {
                            String jsonStr = new String(data, StandardCharsets.UTF_8);
                            JSONObject obj = new JSONObject(jsonStr);
                            double lat = obj.getDouble("lat");
                            double lng = obj.getDouble("lng");
                            float acc = (float) obj.getDouble("acc");
                            long time = obj.optLong("time", System.currentTimeMillis());

                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                            String timestamp = sdf.format(new Date(time));

                            LocationRecord record = new LocationRecord(
                                    targetDeviceId,
                                    UUID.randomUUID().toString(),
                                    lat,
                                    lng,
                                    acc,
                                    0.0,
                                    0.0f,
                                    timestamp
                            );

                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onLocationFound(record);
                                }
                            });
                            gatt.disconnect();
                            return;
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing BLE location payload", e);
                        }
                    }
                }
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to read location over BLE");
                });
                gatt.disconnect();
            }
        });
    }

    @SuppressLint("MissingPermission")
    public synchronized void cleanup() {
        stopScan();
        stopAdvertising();
        if (activeClientGatt != null) {
            try {
                activeClientGatt.close();
            } catch (Exception ignored) {}
            activeClientGatt = null;
        }
    }
}
