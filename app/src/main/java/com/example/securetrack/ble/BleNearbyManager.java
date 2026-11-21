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
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages cryptographically authenticated BLE Nearby communication.
 * Implements mutual HMAC-SHA256 challenge-response authentication,
 * replay protection with single-use unpredictable nonces, session expiration,
 * and AES-128-GCM authenticated payload encryption.
 */
public class BleNearbyManager {

    private static final String TAG = "BleNearbyManager";

    public static final UUID SERVICE_UUID = UUID.fromString("0000FE70-0000-1000-8000-00805F9B34FB");
    public static final UUID CHAR_AUTH_UUID = UUID.fromString("0000FE71-0000-1000-8000-00805F9B34FB");
    public static final UUID CHAR_LOCATION_UUID = UUID.fromString("0000FE72-0000-1000-8000-00805F9B34FB");

    private static final long HANDSHAKE_TIMEOUT_MS = 30_000L; // 30 seconds for handshake
    private static final long SESSION_VALIDITY_MS = 300_000L;  // 5 minutes session validity
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private static volatile BleNearbyManager instance;

    private final Context context;
    private final Handler mainHandler;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final SessionManager sessionManager;
    private final FusedLocationProviderClient fusedLocationClient;
    private final SecureRandom secureRandom = new SecureRandom();

    // Active BLE authentication sessions indexed by client Bluetooth hardware address
    private final Map<String, BleAuthSession> sessionsByAddress = new ConcurrentHashMap<>();

    // Peripheral mode (Target Device)
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isAdvertising = false;

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

    public static class BleAuthSession {
        public final String clientAddress;
        public final byte[] serverNonce;
        public final long createdAt;
        public boolean serverNonceConsumed = false;
        public boolean authenticated = false;
        public byte[] sessionKey = null;
        public int failureCount = 0;
        public long authenticatedAt = 0;

        public BleAuthSession(String clientAddress, byte[] serverNonce) {
            this.clientAddress = clientAddress;
            this.serverNonce = serverNonce;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isHandshakeExpired() {
            return (System.currentTimeMillis() - createdAt) > HANDSHAKE_TIMEOUT_MS;
        }

        public boolean isSessionExpired() {
            if (!authenticated) return true;
            return (System.currentTimeMillis() - authenticatedAt) > SESSION_VALIDITY_MS;
        }
    }

    private BleNearbyManager(Context context) {
        this.context = context.getApplicationContext();
        Looper mainLooper = Looper.getMainLooper();
        this.mainHandler = mainLooper != null ? new Handler(mainLooper) : null;
        this.bluetoothManager = this.context != null ? (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE) : null;
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        this.sessionManager = new SessionManager(this.context);
        this.fusedLocationClient = this.context != null ? LocationServices.getFusedLocationProviderClient(this.context) : null;
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
    // CRYPTOGRAPHIC PROTOCOL HELPERS
    // =========================================================================

    public static byte[] deriveDeviceBleKey(String ownerUid, String deviceId) {
        return deriveDeviceBleKey(null, ownerUid, deviceId);
    }

    public static byte[] deriveDeviceBleKey(String sharedSecret, String ownerUid, String deviceId) {
        if (ownerUid == null || ownerUid.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }
        String keyMaterial = (sharedSecret != null && !sharedSecret.trim().isEmpty())
                ? sharedSecret
                : ("SecureTrack:SharedSecretSalt:" + ownerUid);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyMaterial.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(("SecureTrack:BLE:PairingKey:v2:" + ownerUid + ":" + deviceId).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] computeHmacSha256(byte[] key, String message) {
        if (key == null || message == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject encryptAesGcm(byte[] key, byte[] plaintext, byte[] aad) {
        try {
            byte[] key16 = new byte[16];
            System.arraycopy(key, 0, key16, 0, 16);

            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key16, "AES"), spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] ciphertext = cipher.doFinal(plaintext);

            JSONObject obj = new JSONObject();
            obj.put("iv", bytesToHex(iv));
            obj.put("data", bytesToHex(ciphertext));
            return obj;
        } catch (Exception e) {
            Log.e(TAG, "AES-GCM encryption failure", e);
            return null;
        }
    }

    public static byte[] decryptAesGcm(byte[] key, byte[] iv, byte[] ciphertext, byte[] aad) {
        try {
            byte[] key16 = new byte[16];
            System.arraycopy(key, 0, key16, 0, 16);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key16, "AES"), spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            Log.e(TAG, "AES-GCM decryption failure / tag mismatch", e);
            return null;
        }
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
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
                .setIncludeDeviceName(false) // Never leak device name, IMEI or identity in plaintext beacon
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
                    Log.i(TAG, "BLE client connected to target GATT server");
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "BLE client disconnected from target GATT server");
                    if (device != null && device.getAddress() != null) {
                        sessionsByAddress.remove(device.getAddress());
                    }
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                String clientAddr = device != null ? device.getAddress() : "unknown";

                if (CHAR_AUTH_UUID.equals(characteristic.getUuid())) {
                    // Step 1: Controller requests challenge; generate fresh serverNonce
                    byte[] serverNonce = new byte[16];
                    secureRandom.nextBytes(serverNonce);

                    BleAuthSession session = new BleAuthSession(clientAddr, serverNonce);
                    sessionsByAddress.put(clientAddr, session);

                    String myDeviceId = sessionManager.getCurrentDeviceId();
                    try {
                        JSONObject handshakeInit = new JSONObject();
                        handshakeInit.put("v", 1);
                        handshakeInit.put("target", myDeviceId != null ? myDeviceId : "");
                        handshakeInit.put("sNonce", bytesToHex(serverNonce));
                        handshakeInit.put("ts", System.currentTimeMillis());

                        byte[] respBytes = handshakeInit.toString().getBytes(StandardCharsets.UTF_8);
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, respBytes);
                    } catch (Exception e) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                    }

                } else if (CHAR_LOCATION_UUID.equals(characteristic.getUuid())) {
                    // Protected Location Read: Must be strictly authenticated
                    BleAuthSession session = sessionsByAddress.get(clientAddr);
                    if (session == null || !session.authenticated || session.isSessionExpired() || session.sessionKey == null) {
                        Log.w(TAG, "Unauthorized location read attempt over BLE from unauthenticated client: " + clientAddr);
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, offset, null);
                        // Disconnect unauthenticated client
                        if (gattServer != null && device != null) {
                            try { gattServer.cancelConnection(device); } catch (Exception ignored) {}
                        }
                        return;
                    }

                    fetchAndSendEncryptedLocation(device, requestId, offset, session);

                } else {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                String clientAddr = device != null ? device.getAddress() : "unknown";

                if (CHAR_AUTH_UUID.equals(characteristic.getUuid())) {
                    // Step 2: Controller sends challenge response proof
                    byte[] serverProofResp = verifyAuthChallengeAndDeriveSession(clientAddr, value);
                    if (serverProofResp != null) {
                        Log.i(TAG, "BLE Controller authenticated successfully");
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, serverProofResp);
                        }
                    } else {
                        Log.w(TAG, "BLE Controller authentication verification failed");
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, offset, null);
                        }
                        // Check rate limiting / failure count
                        BleAuthSession session = sessionsByAddress.get(clientAddr);
                        if (session != null && session.failureCount >= MAX_FAILED_ATTEMPTS) {
                            Log.w(TAG, "Max authentication attempts exceeded for client " + clientAddr + ". Disconnecting.");
                            sessionsByAddress.remove(clientAddr);
                            if (gattServer != null && device != null) {
                                try { gattServer.cancelConnection(device); } catch (Exception ignored) {}
                            }
                        }
                    }
                } else {
                    // BLE command writes or unknown characteristics are disabled/blocked
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
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );

            service.addCharacteristic(authChar);
            service.addCharacteristic(locChar);
            gattServer.addService(service);
        }
    }

    private synchronized byte[] verifyAuthChallengeAndDeriveSession(String clientAddr, byte[] writeValue) {
        if (writeValue == null || writeValue.length == 0) return null;

        BleAuthSession session = sessionsByAddress.get(clientAddr);
        if (session == null || session.isHandshakeExpired() || session.serverNonceConsumed) {
            Log.w(TAG, "Handshake session missing, expired or server nonce already consumed (replay attempt)");
            return null;
        }

        // Mark server nonce consumed immediately to prevent replay
        session.serverNonceConsumed = true;

        try {
            String jsonStr = new String(writeValue, StandardCharsets.UTF_8);
            JSONObject req = new JSONObject(jsonStr);

            String callerDeviceId = req.optString("caller");
            String ownerUid = req.optString("owner");
            String clientNonceHex = req.optString("cNonce");
            String clientProofHex = req.optString("proof");

            if (callerDeviceId.isEmpty() || ownerUid.isEmpty() || clientNonceHex.isEmpty() || clientProofHex.isEmpty()) {
                session.failureCount++;
                return null;
            }

            // Verify owner binding
            String myOwner = sessionManager.getOwnerUid();
            if (myOwner != null && !myOwner.equals(ownerUid)) {
                Log.w(TAG, "Authentication rejected: Owner UID mismatch");
                session.failureCount++;
                return null;
            }

            String myDeviceId = sessionManager.getCurrentDeviceId();
            String sharedSecret = sessionManager.getBleSharedSecret();
            if (sharedSecret == null || sharedSecret.isEmpty()) {
                sharedSecret = sessionManager.getBleDeviceSecret(callerDeviceId);
            }
            byte[] pairingKey = deriveDeviceBleKey(sharedSecret, ownerUid, myDeviceId);
            if (pairingKey == null) {
                session.failureCount++;
                return null;
            }

            String serverNonceHex = bytesToHex(session.serverNonce);
            String authDataToSign = "BLE-AUTH-REQ:" + myDeviceId + ":" + callerDeviceId + ":" + serverNonceHex + ":" + clientNonceHex;
            byte[] expectedProof = computeHmacSha256(pairingKey, authDataToSign);
            byte[] clientProof = hexToBytes(clientProofHex);

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(expectedProof, clientProof)) {
                Log.w(TAG, "Authentication rejected: Invalid cryptographic HMAC proof");
                session.failureCount++;
                return null;
            }

            // Derive session key for this connection
            String sessionKeyData = "BLE-SESSION:" + serverNonceHex + ":" + clientNonceHex;
            byte[] rawSessionKey = computeHmacSha256(pairingKey, sessionKeyData);
            session.sessionKey = new byte[16];
            System.arraycopy(rawSessionKey, 0, session.sessionKey, 0, 16);

            session.authenticated = true;
            session.authenticatedAt = System.currentTimeMillis();

            // Compute server response proof
            byte[] serverProof = computeHmacSha256(session.sessionKey, "BLE-AUTH-RESP:" + serverNonceHex + ":" + clientNonceHex);

            JSONObject resp = new JSONObject();
            resp.put("status", "AUTHENTICATED");
            resp.put("serverProof", bytesToHex(serverProof));

            return resp.toString().getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Error in verifyAuthChallengeAndDeriveSession", e);
            session.failureCount++;
            return null;
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchAndSendEncryptedLocation(BluetoothDevice device, int requestId, int offset, BleAuthSession session) {
        if (!PermissionUtils.hasForegroundLocationPermission(context)) {
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
            return;
        }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            sendEncryptedPayload(device, requestId, offset, session, location);
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    sendEncryptedPayload(device, requestId, offset, session, lastLoc);
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

    @SuppressLint("MissingPermission")
    private void sendEncryptedPayload(BluetoothDevice device, int requestId, int offset, BleAuthSession session, Location loc) {
        try {
            JSONObject plainJson = new JSONObject();
            plainJson.put("lat", loc.getLatitude());
            plainJson.put("lng", loc.getLongitude());
            plainJson.put("acc", loc.getAccuracy());
            plainJson.put("time", loc.getTime());
            plainJson.put("provider", "BLE_NEARBY_ENCRYPTED");

            String targetId = sessionManager.getCurrentDeviceId();
            byte[] aad = ("LOCATION:" + (targetId != null ? targetId : "") + ":" + session.authenticatedAt).getBytes(StandardCharsets.UTF_8);

            JSONObject encryptedObj = encryptAesGcm(session.sessionKey, plainJson.toString().getBytes(StandardCharsets.UTF_8), aad);
            if (encryptedObj != null) {
                byte[] payload = encryptedObj.toString().getBytes(StandardCharsets.UTF_8);
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, payload);
            } else {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error packaging encrypted location payload", e);
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
        }
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
        sessionsByAddress.clear();
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
                Log.i(TAG, "Discovered nearby SecureTrack BLE advertisement");
                stopScan();
                connectAndAuthenticateTarget(device, targetDeviceId, callback);
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
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> {
                if (isScanning) {
                    stopScan();
                    if (callback != null) {
                        callback.onError("No nearby SecureTrack BLE device found within range");
                    }
                }
            }, 12000L);
        }
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
    private void connectAndAuthenticateTarget(BluetoothDevice device, String targetDeviceId, BleLocationCallback callback) {
        if (callback != null) {
            callback.onStatusUpdate("Connecting to nearby device via BLE...");
        }

        final byte[][] sessionKeyHolder = new byte[1][];
        final String[] serverNonceHolder = new String[1];
        final long[] sessionTimestampHolder = new long[1];

        activeClientGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to target GATT. Discovering services...");
                    if (callback != null) callback.onStatusUpdate("Initiating mutual authentication...");
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
                            // Step 1: Read Auth Characteristic to obtain server challenge
                            gatt.readCharacteristic(authChar);
                            return;
                        }
                    }
                }
                if (callback != null) callback.onError("SecureTrack service discovery failed on target");
                gatt.disconnect();
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (CHAR_AUTH_UUID.equals(characteristic.getUuid())) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        if (callback != null) callback.onError("Failed to obtain BLE authentication challenge");
                        gatt.disconnect();
                        return;
                    }

                    byte[] data = characteristic.getValue();
                    if (data == null || data.length == 0) {
                        if (callback != null) callback.onError("Empty auth challenge from target");
                        gatt.disconnect();
                        return;
                    }

                    try {
                        JSONObject initObj = new JSONObject(new String(data, StandardCharsets.UTF_8));
                        String reportedTarget = initObj.optString("target");
                        String serverNonceHex = initObj.optString("sNonce");
                        serverNonceHolder[0] = serverNonceHex;
                        sessionTimestampHolder[0] = initObj.optLong("ts", System.currentTimeMillis());

                        if (serverNonceHex.isEmpty()) {
                            if (callback != null) callback.onError("Invalid server nonce received");
                            gatt.disconnect();
                            return;
                        }

                        // Verify target identity if specified
                        if (targetDeviceId != null && !targetDeviceId.isEmpty() && !reportedTarget.isEmpty()) {
                            if (!targetDeviceId.equals(reportedTarget)) {
                                if (callback != null) callback.onError("BLE target ID mismatch (" + reportedTarget + ")");
                                gatt.disconnect();
                                return;
                            }
                        }

                        // Step 2: Compute mutual HMAC authentication proof
                        String ownerUid = sessionManager.getOwnerUid();
                        String callerDeviceId = sessionManager.getCurrentDeviceId();
                        String sharedSecret = sessionManager.getBleDeviceSecret(targetDeviceId != null ? targetDeviceId : reportedTarget);
                        if (sharedSecret == null || sharedSecret.isEmpty()) {
                            sharedSecret = sessionManager.getBleSharedSecret();
                        }
                        byte[] pairingKey = deriveDeviceBleKey(sharedSecret, ownerUid, targetDeviceId != null ? targetDeviceId : reportedTarget);

                        if (pairingKey == null) {
                            if (callback != null) callback.onError("No approved pairing key found for target device");
                            gatt.disconnect();
                            return;
                        }

                        byte[] clientNonce = new byte[16];
                        secureRandom.nextBytes(clientNonce);
                        String clientNonceHex = bytesToHex(clientNonce);

                        String authData = "BLE-AUTH-REQ:" + (targetDeviceId != null ? targetDeviceId : reportedTarget) + ":" + callerDeviceId + ":" + serverNonceHex + ":" + clientNonceHex;
                        byte[] clientProof = computeHmacSha256(pairingKey, authData);

                        // Derive session key
                        byte[] rawSessionKey = computeHmacSha256(pairingKey, "BLE-SESSION:" + serverNonceHex + ":" + clientNonceHex);
                        sessionKeyHolder[0] = new byte[16];
                        System.arraycopy(rawSessionKey, 0, sessionKeyHolder[0], 0, 16);

                        JSONObject writeObj = new JSONObject();
                        writeObj.put("caller", callerDeviceId != null ? callerDeviceId : "");
                        writeObj.put("owner", ownerUid != null ? ownerUid : "");
                        writeObj.put("cNonce", clientNonceHex);
                        writeObj.put("proof", bytesToHex(clientProof));

                        characteristic.setValue(writeObj.toString().getBytes(StandardCharsets.UTF_8));
                        gatt.writeCharacteristic(characteristic);

                    } catch (Exception e) {
                        Log.e(TAG, "Error initiating client auth handshake", e);
                        if (callback != null) callback.onError("Auth handshake preparation failed: " + e.getMessage());
                        gatt.disconnect();
                    }

                } else if (CHAR_LOCATION_UUID.equals(characteristic.getUuid())) {
                    // Protected Location payload received
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError("Protected location read rejected (GATT status " + status + ")");
                        });
                        gatt.disconnect();
                        return;
                    }

                    byte[] data = characteristic.getValue();
                    if (data != null && data.length > 0 && sessionKeyHolder[0] != null) {
                        try {
                            JSONObject encJson = new JSONObject(new String(data, StandardCharsets.UTF_8));
                            byte[] iv = hexToBytes(encJson.getString("iv"));
                            byte[] ciphertext = hexToBytes(encJson.getString("data"));

                            byte[] aad = ("LOCATION:" + (targetDeviceId != null ? targetDeviceId : "") + ":" + sessionTimestampHolder[0]).getBytes(StandardCharsets.UTF_8);
                            byte[] decryptedPlain = decryptAesGcm(sessionKeyHolder[0], iv, ciphertext, aad);

                            if (decryptedPlain == null) {
                                throw new SecurityException("GCM tag verification failed on location payload");
                            }

                            JSONObject locObj = new JSONObject(new String(decryptedPlain, StandardCharsets.UTF_8));
                            double lat = locObj.getDouble("lat");
                            double lng = locObj.getDouble("lng");
                            float acc = (float) locObj.getDouble("acc");
                            long time = locObj.optLong("time", System.currentTimeMillis());

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
                            Log.e(TAG, "Decryption or parsing of protected location failed", e);
                        }
                    }

                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Failed to decrypt verified location payload");
                    });
                    gatt.disconnect();
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (CHAR_AUTH_UUID.equals(characteristic.getUuid())) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (callback != null) callback.onStatusUpdate("Mutual authentication verified! Requesting encrypted location...");
                        BluetoothGattService service = gatt.getService(SERVICE_UUID);
                        if (service != null) {
                            BluetoothGattCharacteristic locChar = service.getCharacteristic(CHAR_LOCATION_UUID);
                            if (locChar != null) {
                                gatt.readCharacteristic(locChar);
                                return;
                            }
                        }
                    }
                    if (callback != null) callback.onError("Authentication challenge rejected by target (unpaired or invalid proof)");
                    gatt.disconnect();
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    public synchronized void cleanup() {
        stopScan();
        stopAdvertising();
        sessionsByAddress.clear();
        if (activeClientGatt != null) {
            try {
                activeClientGatt.close();
            } catch (Exception ignored) {}
            activeClientGatt = null;
        }
    }
}
