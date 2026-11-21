package com.example.securetrack.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.securetrack.MainActivity;
import com.example.securetrack.R;
import com.example.securetrack.activities.DeviceSecurityActivity;
import com.example.securetrack.activities.LocationHistoryActivity;
import com.example.securetrack.adapters.DeviceSelectorAdapter;
import com.example.securetrack.databinding.FragmentTrackingBinding;
import com.example.securetrack.location.LocationTrackingService;
import com.example.securetrack.models.Device;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class TrackingFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    public static final OnlineTileSourceBase OSM_REAL_STREETS = new XYTileSource(
            "OpenStreetMap",
            0, 19, 256, ".png",
            new String[]{
                    "https://a.tile.openstreetmap.org/",
                    "https://b.tile.openstreetmap.org/",
                    "https://c.tile.openstreetmap.org/"
            },
            "© OpenStreetMap contributors",
            new TileSourcePolicy(2,
                    TileSourcePolicy.FLAG_NO_BULK
                            | TileSourcePolicy.FLAG_NO_PREVENTIVE
                            | TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
            )
    );

    private FragmentTrackingBinding binding;
    private MapView map;
    private DeviceRepository deviceRepository;
    private LocationRepository locationRepository;
    private SecurityRepository securityRepository;
    private SessionManager sessionManager;
    private FusedLocationProviderClient fusedLocationClient;

    private DeviceSelectorAdapter deviceSelectorAdapter;
    private final List<Device> userDevices = new ArrayList<>();
    private String selectedDeviceId;
    private Device selectedDevice;
    private Marker currentLocationMarker;

    private ListenerRegistration devicesListener;
    private ListenerRegistration selectedDeviceListener;
    private ListenerRegistration selectedLocationListener;
    private ListenerRegistration commandStatusListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("SecureTrack/1.0 (dhanushs602848@gmail.com; Android Tracker)");
        File basePath = new File(ctx.getCacheDir(), "osmdroid");
        File tileCache = new File(basePath, "tiles");
        if (!tileCache.exists()) {
            tileCache.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(basePath);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);

        binding = FragmentTrackingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        deviceRepository = new DeviceRepository(requireContext());
        locationRepository = new LocationRepository(requireContext());
        securityRepository = new SecurityRepository(requireContext());
        sessionManager = new SessionManager(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        map = binding.mapView;
        map.setTileSource(OSM_REAL_STREETS);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(false);
        map.setMinZoomLevel(4.0);
        map.setMaxZoomLevel(19.0);
        map.getController().setZoom(16.5);

        if (map.getTileProvider() != null) {
            map.getTileProvider().clearTileCache();
        }

        deviceSelectorAdapter = new DeviceSelectorAdapter();
        binding.rvDeviceSelector.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvDeviceSelector.setAdapter(deviceSelectorAdapter);

        deviceSelectorAdapter.setOnDeviceSelectedListener(device -> selectDevice(device.getId()));

        if (getActivity() instanceof MainActivity) {
            String pendingDevId = ((MainActivity) getActivity()).getSelectedDeviceIdForTracking();
            if (pendingDevId != null && !pendingDevId.isEmpty()) {
                this.selectedDeviceId = pendingDevId;
            }
        }

        binding.switchBackgroundTracking.setChecked(sessionManager.isBackgroundTrackingEnabled());
        binding.switchBackgroundTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkAndRequestLocationPermissions();
            } else {
                sessionManager.setBackgroundTrackingEnabled(false);
                stopLocationService();
            }
        });

        binding.btnRefreshLocation.setOnClickListener(v -> onLocateNowClicked());

        binding.btnLocationHistory.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LocationHistoryActivity.class);
            if (selectedDeviceId != null) {
                intent.putExtra("device_id", selectedDeviceId);
                if (selectedDevice != null) {
                    intent.putExtra("device_name", selectedDevice.getDeviceName());
                }
            }
            startActivity(intent);
        });

        binding.btnSecurityControls.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), DeviceSecurityActivity.class);
            if (selectedDeviceId != null) {
                intent.putExtra("device_id", selectedDeviceId);
                if (selectedDevice != null) {
                    intent.putExtra("device_name", selectedDevice.getDeviceName());
                }
            }
            startActivity(intent);
        });

        startListeningToDevices();
    }

    private void startListeningToDevices() {
        stopDevicesListener();

        devicesListener = deviceRepository.listenToUserDevices(new DeviceRepository.ApiCallback<List<Device>>() {
            @Override
            public void onSuccess(List<Device> result) {
                if (!isAdded() || binding == null) return;
                userDevices.clear();
                if (result != null && !result.isEmpty()) {
                    userDevices.addAll(result);
                } else {
                    // Fallback to local device if user has no paired devices in Firestore yet
                    String localId = sessionManager.getCurrentDeviceId();
                    if (localId == null) localId = sessionManager.getInstallationId();
                    Device localDev = new Device(
                            localId,
                            sessionManager.getDeviceName() != null ? sessionManager.getDeviceName() : DeviceUtils.getDeviceName(),
                            DeviceUtils.getDeviceModel(),
                            DeviceUtils.getAndroidVersion(),
                            DeviceUtils.getBatteryPercentage(requireContext()),
                            "ONLINE",
                            true,
                            localId
                    );
                    userDevices.add(localDev);
                }

                if (selectedDeviceId == null || !deviceListContains(userDevices, selectedDeviceId)) {
                    selectedDeviceId = userDevices.get(0).getId();
                }

                deviceSelectorAdapter.setDevices(userDevices, selectedDeviceId);
                subscribeToSelectedDevice(selectedDeviceId);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || binding == null) return;
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean deviceListContains(List<Device> list, String devId) {
        if (list == null || devId == null) return false;
        for (Device d : list) {
            if (devId.equals(d.getId())) return true;
        }
        return false;
    }

    private void selectDevice(String deviceId) {
        if (deviceId == null || deviceId.equals(this.selectedDeviceId)) return;
        this.selectedDeviceId = deviceId;
        deviceSelectorAdapter.setSelectedDeviceId(deviceId);
        subscribeToSelectedDevice(deviceId);
    }

    private void subscribeToSelectedDevice(String deviceId) {
        stopSelectedDeviceListeners();

        if (deviceId == null) return;

        // 1. Real-time document snapshot listener for device telemetry
        selectedDeviceListener = deviceRepository.listenToSelectedDevice(deviceId, new DeviceRepository.ApiCallback<Device>() {
            @Override
            public void onSuccess(Device dev) {
                if (!isAdded() || binding == null) return;
                selectedDevice = dev;
                updateDeviceTelemetryUi(dev);
            }

            @Override
            public void onError(String errorMessage) {
                // Ignore transient errors
            }
        });

        // 2. Real-time snapshot listener for latest location record
        selectedLocationListener = locationRepository.listenToLatestLocation(deviceId, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord record) {
                if (!isAdded() || binding == null) return;
                displayRecordOnMap(record);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || binding == null) return;
                if (selectedDevice != null && selectedDevice.getLastLatitude() != null && selectedDevice.getLastLongitude() != null) {
                    displayCoordinatesOnMap(
                            selectedDevice.getLastLatitude(),
                            selectedDevice.getLastLongitude(),
                            selectedDevice.getLocationAccuracy() != null ? selectedDevice.getLocationAccuracy() : 0.0f,
                            selectedDevice.getFormattedLastSeen()
                    );
                } else {
                    showNoLocationOverlay();
                }
            }
        });
    }

    private void updateDeviceTelemetryUi(Device dev) {
        if (!isAdded() || binding == null || dev == null) return;

        String name = dev.getDeviceName();
        if (name == null || name.trim().isEmpty() || name.matches("^[0-9a-fA-F-]{32,36}$")) {
            name = (dev.getModel() != null && !dev.getModel().isEmpty()) ? dev.getModel() : "Protected Device";
        }

        boolean isLocalPhone = isLocalDevice(dev.getId());
        if (isLocalPhone) {
            name += " (This Device)";
        }

        binding.tvSelectedDeviceName.setText(name);

        String modelText = "Model: " + (dev.getModel() != null ? dev.getModel() : "Android") +
                (dev.getAndroidVersion() != null ? " (Android " + dev.getAndroidVersion() + ")" : "");
        binding.tvSelectedDeviceModel.setText(modelText);

        boolean isOnline = dev.isOnline();
        if (isOnline) {
            binding.tvSelectedDeviceStatus.setText(R.string.status_online);
            binding.tvSelectedDeviceStatus.setBackgroundResource(R.drawable.bg_badge_success);
            binding.tvSelectedDeviceStatus.setTextColor(requireContext().getColor(R.color.status_success));
            binding.tvStaleNotice.setVisibility(View.GONE);
        } else {
            String statusStr = dev.getStatus() != null ? dev.getStatus() : "OFFLINE";
            binding.tvSelectedDeviceStatus.setText(statusStr.toUpperCase());
            binding.tvSelectedDeviceStatus.setBackgroundResource(R.drawable.bg_chip_stale);
            binding.tvSelectedDeviceStatus.setTextColor(requireContext().getColor(R.color.text_secondary));

            if (dev.isLocationStale()) {
                binding.tvStaleNotice.setVisibility(View.VISIBLE);
                binding.tvStaleNotice.setText("Device offline or location stale (>15m). Tap 'Locate Now' to dispatch remote GPS request.");
            } else {
                binding.tvStaleNotice.setVisibility(View.GONE);
            }
        }

        Integer battery = dev.getBatteryLevel();
        binding.tvTelemetryBattery.setText("Battery: " + (battery != null ? battery + "%" : "N/A"));
        binding.tvTelemetryAccuracy.setText("Accuracy: " + dev.getFormattedAccuracy());
        binding.tvTelemetryUpdated.setText("Updated: " + dev.getFormattedLastSeen());

        // Local background tracking switch toggle visibility
        if (isLocalPhone) {
            binding.layoutLocalTrackingSwitch.setVisibility(View.VISIBLE);
            binding.switchBackgroundTracking.setChecked(sessionManager.isBackgroundTrackingEnabled());
        } else {
            binding.layoutLocalTrackingSwitch.setVisibility(View.GONE);
        }

        if (dev.getLastLatitude() != null && dev.getLastLongitude() != null) {
            displayCoordinatesOnMap(
                    dev.getLastLatitude(),
                    dev.getLastLongitude(),
                    dev.getLocationAccuracy() != null ? dev.getLocationAccuracy() : 0.0f,
                    dev.getFormattedLastSeen()
            );
        }
    }

    private boolean isLocalDevice(String deviceId) {
        if (deviceId == null) return false;
        String currentDevId = sessionManager.getCurrentDeviceId();
        String installId = sessionManager.getInstallationId();
        return deviceId.equals(currentDevId) || deviceId.equals(installId);
    }

    private void displayCoordinatesOnMap(double lat, double lng, float accuracy, String updatedTime) {
        if (!isAdded() || map == null || binding == null) return;

        binding.cardNoLocation.setVisibility(View.GONE);

        GeoPoint point = new GeoPoint(lat, lng);
        map.getController().setCenter(point);

        if (currentLocationMarker == null) {
            currentLocationMarker = new Marker(map);
            currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(currentLocationMarker);
        }

        String targetName = selectedDevice != null ? selectedDevice.getDeviceName() : "Selected Device";
        currentLocationMarker.setPosition(point);
        currentLocationMarker.setTitle(targetName);
        currentLocationMarker.setSnippet(String.format(Locale.US, "Lat: %.5f, Lng: %.5f (±%.0fm) • %s",
                lat, lng, accuracy, updatedTime));
        currentLocationMarker.showInfoWindow();
        map.invalidate();
    }

    private void displayRecordOnMap(LocationRecord record) {
        if (!isAdded() || map == null || binding == null || record == null) return;

        displayCoordinatesOnMap(
                record.getLatitude(),
                record.getLongitude(),
                record.getAccuracy() != null ? record.getAccuracy() : 0.0f,
                record.getTimestamp() != null ? record.getTimestamp() : "Recorded Fix"
        );
    }

    private void showNoLocationOverlay() {
        if (!isAdded() || binding == null) return;
        binding.cardNoLocation.setVisibility(View.VISIBLE);
        if (currentLocationMarker != null && map != null) {
            map.getOverlays().remove(currentLocationMarker);
            currentLocationMarker = null;
            map.invalidate();
        }
    }

    private void onLocateNowClicked() {
        if (selectedDeviceId == null) {
            Toast.makeText(getContext(), "No device selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isLocalDevice(selectedDeviceId)) {
            requestFreshLocalGpsLocation();
        } else {
            dispatchRemoteLocateCommand(selectedDeviceId);
        }
    }

    private void dispatchRemoteLocateCommand(String targetDeviceId) {
        if (!isAdded() || binding == null) return;

        String targetName = selectedDevice != null ? selectedDevice.getDeviceName() : "Remote Device";
        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(getContext(), "Locating " + targetName + " remotely...", Toast.LENGTH_SHORT).show();

        DeviceCommand cmd = new DeviceCommand("LOCATE_NOW", "QUEUED", "Remote locate from controller dashboard", "");
        securityRepository.createCommand(targetDeviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand createdCommand) {
                if (!isAdded() || binding == null) return;
                listenForCommandCompletion(targetDeviceId, createdCommand.getId());
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Failed to dispatch locate request: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listenForCommandCompletion(String targetDeviceId, String commandId) {
        stopCommandStatusListener();

        String ownerUid = sessionManager.getOwnerUid();
        if (ownerUid == null) {
            if (binding != null) binding.progressBar.setVisibility(View.GONE);
            return;
        }

        // Timeout fallback after 15 seconds if device is offline
        Runnable timeoutRunnable = () -> {
            if (isAdded() && binding != null && commandStatusListener != null) {
                stopCommandStatusListener();
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Locate command queued. Device may be offline or out of range.", Toast.LENGTH_LONG).show();
            }
        };
        mainHandler.postDelayed(timeoutRunnable, 15000L);

        commandStatusListener = FirebaseFirestore.getInstance()
                .collection("users").document(ownerUid)
                .collection("devices").document(targetDeviceId)
                .collection("commands").document(commandId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;

                    String status = doc.getString("status");
                    String result = doc.getString("result");

                    if ("EXECUTED".equalsIgnoreCase(status)) {
                        mainHandler.removeCallbacks(timeoutRunnable);
                        stopCommandStatusListener();
                        if (isAdded() && binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Fresh location fix acquired!", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("FAILED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
                        mainHandler.removeCallbacks(timeoutRunnable);
                        stopCommandStatusListener();
                        if (isAdded() && binding != null) {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Locate failed: " + (result != null ? result : status), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    public void requestFreshLocalGpsLocation() {
        if (!hasLocationPermission()) {
            checkAndRequestLocationPermissions();
            return;
        }

        if (binding != null) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(15000)
                .setMaxUpdateAgeMillis(10000)
                .build();

        CancellationTokenSource cts = new CancellationTokenSource();

        try {
            fusedLocationClient.getCurrentLocation(request, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            handleLocalLocationFix(location);
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    handleLocalLocationFix(lastLoc);
                                } else {
                                    if (binding != null) binding.progressBar.setVisibility(View.GONE);
                                    Context context = getContext();
                                    if (context != null) {
                                        Toast.makeText(context, "GPS searching... Please ensure Location Services are enabled.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }).addOnFailureListener(e -> {
                                if (binding != null) binding.progressBar.setVisibility(View.GONE);
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (binding != null) binding.progressBar.setVisibility(View.GONE);
                    });
        } catch (SecurityException se) {
            if (binding != null) binding.progressBar.setVisibility(View.GONE);
        }
    }

    private void handleLocalLocationFix(@NonNull Location location) {
        if (!isAdded() || map == null || binding == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String isoTimestamp = sdf.format(new Date(location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));

        LocationRecord record = new LocationRecord(
                sessionManager.getCurrentDeviceId(),
                UUID.randomUUID().toString(),
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getAltitude(),
                location.getSpeed(),
                isoTimestamp
        );

        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord result) {
                if (!isAdded() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Local location updated & saved", Toast.LENGTH_SHORT).show();
                displayRecordOnMap(result);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    private boolean hasLocationPermission() {
        Context context = getContext();
        if (context == null) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkAndRequestLocationPermissions() {
        if (!hasLocationPermission()) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            startTracking();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestFreshLocalGpsLocation();
                if (binding != null && binding.switchBackgroundTracking.isChecked()) {
                    startTracking();
                }
            } else {
                if (binding != null) binding.switchBackgroundTracking.setChecked(false);
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "Location permission required to locate device", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startTracking() {
        Context context = getContext();
        if (context == null) return;

        if (!PermissionUtils.hasForegroundLocationPermission(context)) {
            if (binding != null) binding.switchBackgroundTracking.setChecked(false);
            Toast.makeText(context, "Location permission required to enable tracking", Toast.LENGTH_SHORT).show();
            checkAndRequestLocationPermissions();
            return;
        }

        if (!PermissionUtils.isLocationServiceEnabled(context)) {
            if (binding != null) binding.switchBackgroundTracking.setChecked(false);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Location Services (GPS) Disabled")
                    .setMessage("Device GPS is currently disabled in system settings. Please enable Location Services so SecureTrack can track this device.")
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        sessionManager.setBackgroundTrackingEnabled(true);
        Intent serviceIntent = new Intent(context, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        Toast.makeText(context, "Background tracking active", Toast.LENGTH_SHORT).show();
    }

    private void stopLocationService() {
        Context context = getContext();
        if (context == null) return;
        Intent serviceIntent = new Intent(context, LocationTrackingService.class);
        context.stopService(serviceIntent);
        Toast.makeText(context, "Background tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void stopDevicesListener() {
        if (devicesListener != null) {
            devicesListener.remove();
            devicesListener = null;
        }
    }

    private void stopSelectedDeviceListeners() {
        if (selectedDeviceListener != null) {
            selectedDeviceListener.remove();
            selectedDeviceListener = null;
        }
        if (selectedLocationListener != null) {
            selectedLocationListener.remove();
            selectedLocationListener = null;
        }
        stopCommandStatusListener();
    }

    private void stopCommandStatusListener() {
        if (commandStatusListener != null) {
            commandStatusListener.remove();
            commandStatusListener = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
        startListeningToDevices();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
        stopSelectedDeviceListeners();
        stopDevicesListener();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopSelectedDeviceListeners();
        stopDevicesListener();
        if (map != null) {
            map.onDetach();
            map = null;
        }
        currentLocationMarker = null;
        binding = null;
    }
}
