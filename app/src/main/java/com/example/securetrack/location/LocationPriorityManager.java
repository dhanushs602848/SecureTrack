package com.example.securetrack.location;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.securetrack.ble.BleNearbyManager;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.DeviceEntity;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.models.ResolvedLocation;
import com.example.securetrack.repositories.DeviceRepository;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Location Priority Manager: Selects the best available location pathway:
 * 1. Cloud / Firebase Location (LOCATE_NOW live trigger)
 * 2. Authenticated BLE Nearby Fallback
 * 3. Last Known Location Snapshot (clearly marked as STALE / LAST SEEN)
 * 4. Find Hub / OS Offline Finding Guidance
 */
public class LocationPriorityManager {

    private static final String TAG = "LocationPriorityMgr";
    private static final long CLOUD_TIMEOUT_MS = 10000L; // 10s wait for cloud response

    private final Context context;
    private final Handler mainHandler;
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final SecurityRepository securityRepository;
    private final LocationRepository locationRepository;
    private final BleNearbyManager bleNearbyManager;
    private final AppDatabase appDatabase;

    public interface LocationResolutionCallback {
        void onLocationResolved(ResolvedLocation location);
        void onProgress(String statusUpdate);
        void onError(String message);
    }

    public LocationPriorityManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(this.context);
        this.securityRepository = new SecurityRepository(this.context);
        this.locationRepository = new LocationRepository(this.context);
        this.bleNearbyManager = BleNearbyManager.getInstance(this.context);
        this.appDatabase = AppDatabase.getInstance(this.context);
    }

    /**
     * Resolves target location through the 4-tier hierarchy.
     */
    public void resolveTargetLocation(String targetDeviceId, LocationResolutionCallback callback) {
        if (targetDeviceId == null || targetDeviceId.isEmpty()) {
            if (callback != null) callback.onError("Target device not specified");
            return;
        }

        // TIER 1: Attempt Cloud Locate
        attemptCloudLocate(targetDeviceId, callback);
    }

    private void attemptCloudLocate(String targetDeviceId, LocationResolutionCallback callback) {
        if (callback != null) {
            callback.onProgress("Priority 1: Dispatching live cloud location command...");
        }

        String uid = sessionManager.getOwnerUid();
        if (uid == null) {
            // Cannot dispatch cloud command without auth; fallback to BLE / cache
            attemptBleFallback(targetDeviceId, callback);
            return;
        }

        DeviceCommand cmd = new DeviceCommand("LOCATE_NOW", "QUEUED", "Location Priority Manager trigger", "");
        final boolean[] responded = {false};

        securityRepository.createCommand(targetDeviceId, cmd, new SecurityRepository.ApiCallback<DeviceCommand>() {
            @Override
            public void onSuccess(DeviceCommand createdCmd) {
                // Listen to location collection for fresh location timestamped after command creation
                long commandTime = System.currentTimeMillis();

                final ListenerRegistration[] locListener = new ListenerRegistration[1];
                locListener[0] = db.collection("users").document(uid)
                        .collection("devices").document(targetDeviceId)
                        .collection("locations")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .addSnapshotListener((snapshots, e) -> {
                            if (e != null || snapshots == null || snapshots.isEmpty()) return;
                            DocumentSnapshot doc = snapshots.getDocuments().get(0);
                            Double lat = doc.getDouble("latitude");
                            Double lng = doc.getDouble("longitude");
                            Double acc = doc.getDouble("accuracy");
                            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");

                            long locTime = ts != null ? ts.toDate().getTime() : 0;
                            // Check if this location is genuinely fresh (within 15 seconds of command)
                            if (lat != null && lng != null && (locTime >= (commandTime - 15000))) {
                                synchronized (responded) {
                                    if (!responded[0]) {
                                        responded[0] = true;
                                        if (locListener[0] != null) locListener[0].remove();

                                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault());
                                        String formatted = sdf.format(new Date(locTime));

                                        ResolvedLocation res = new ResolvedLocation(
                                                ResolvedLocation.Pathway.CLOUD_LIVE,
                                                lat,
                                                lng,
                                                acc != null ? acc.floatValue() : 10.0f,
                                                locTime,
                                                formatted,
                                                "GPS / Fused Provider (Live Cloud)",
                                                true,
                                                -1,
                                                "LIVE"
                                        );
                                        mainHandler.post(() -> {
                                            if (callback != null) callback.onLocationResolved(res);
                                        });
                                    }
                                }
                            }
                        });

                // Cloud timeout handler
                mainHandler.postDelayed(() -> {
                    synchronized (responded) {
                        if (!responded[0]) {
                            responded[0] = true;
                            if (locListener[0] != null) locListener[0].remove();
                            Log.i(TAG, "Cloud locate timed out. Falling back to BLE nearby scanning.");
                            attemptBleFallback(targetDeviceId, callback);
                        }
                    }
                }, CLOUD_TIMEOUT_MS);
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Cloud locate failed: " + errorMessage + ". Falling back to BLE.");
                attemptBleFallback(targetDeviceId, callback);
            }
        });
    }

    private void attemptBleFallback(String targetDeviceId, LocationResolutionCallback callback) {
        if (!bleNearbyManager.isBleSupported() || !bleNearbyManager.isBluetoothEnabled()) {
            if (callback != null) {
                callback.onProgress("Bluetooth unavailable. Skipping to Last Known Location...");
            }
            attemptLastKnownFallback(targetDeviceId, callback);
            return;
        }

        if (callback != null) {
            callback.onProgress("Priority 2: Scanning nearby Bluetooth Low Energy (BLE)...");
        }

        bleNearbyManager.scanAndLocateNearbyDevice(targetDeviceId, new BleNearbyManager.BleLocationCallback() {
            @Override
            public void onLocationFound(LocationRecord record) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault());
                String formatted = sdf.format(new Date());

                ResolvedLocation res = new ResolvedLocation(
                        ResolvedLocation.Pathway.BLE_NEARBY,
                        record.getLatitude(),
                        record.getLongitude(),
                        record.getAccuracy(),
                        System.currentTimeMillis(),
                        formatted,
                        "Authenticated BLE Nearby Peer",
                        true,
                        -1,
                        "BLE NEARBY"
                );
                if (callback != null) {
                    callback.onLocationResolved(res);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.i(TAG, "BLE nearby locate failed: " + errorMessage + ". Falling back to Last Known.");
                attemptLastKnownFallback(targetDeviceId, callback);
            }

            @Override
            public void onStatusUpdate(String status) {
                if (callback != null) callback.onProgress(status);
            }
        });
    }

    private void attemptLastKnownFallback(String targetDeviceId, LocationResolutionCallback callback) {
        if (callback != null) {
            callback.onProgress("Priority 3: Retrieving most recent recorded location...");
        }

        String uid = sessionManager.getOwnerUid();
        if (uid != null) {
            // Query Firestore for last recorded location
            db.collection("users").document(uid)
                    .collection("devices").document(targetDeviceId)
                    .collection("locations")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                            Double lat = doc.getDouble("latitude");
                            Double lng = doc.getDouble("longitude");
                            Double acc = doc.getDouble("accuracy");
                            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");

                            long time = ts != null ? ts.toDate().getTime() : 0;
                            String formatted = formatStaleness(time);

                            ResolvedLocation res = new ResolvedLocation(
                                    ResolvedLocation.Pathway.LAST_KNOWN,
                                    lat != null ? lat : 0.0,
                                    lng != null ? lng : 0.0,
                                    acc != null ? acc.floatValue() : 50.0f,
                                    time,
                                    formatted,
                                    "Cached Snapshot (Historical)",
                                    false,
                                    -1,
                                    "LAST SEEN (STALE)"
                            );
                            if (callback != null) callback.onLocationResolved(res);
                        } else {
                            attemptLocalRoomFallback(targetDeviceId, callback);
                        }
                    })
                    .addOnFailureListener(e -> attemptLocalRoomFallback(targetDeviceId, callback));
        } else {
            attemptLocalRoomFallback(targetDeviceId, callback);
        }
    }

    private void attemptLocalRoomFallback(String targetDeviceId, LocationResolutionCallback callback) {
        new Thread(() -> {
            DeviceEntity cachedDevice = appDatabase.deviceDao().getDeviceById(targetDeviceId);
            if (cachedDevice != null && cachedDevice.getLastLatitude() != 0.0) {
                String formatted = formatStaleness(cachedDevice.getLastLocationTime());
                ResolvedLocation res = new ResolvedLocation(
                        ResolvedLocation.Pathway.LAST_KNOWN,
                        cachedDevice.getLastLatitude(),
                        cachedDevice.getLastLongitude(),
                        cachedDevice.getLastAccuracy(),
                        cachedDevice.getLastLocationTime(),
                        formatted,
                        "Local Device Database",
                        false,
                        cachedDevice.getBatteryLevel(),
                        "LAST SEEN (STALE)"
                );
                mainHandler.post(() -> {
                    if (callback != null) callback.onLocationResolved(res);
                });
            } else {
                // Tier 4: No location recorded; provide Find Hub readiness
                ResolvedLocation res = new ResolvedLocation(
                        ResolvedLocation.Pathway.FIND_HUB_READY,
                        0.0,
                        0.0,
                        0.0f,
                        0,
                        "No recorded location",
                        "Device Offline • Google Find Hub Available",
                        false,
                        -1,
                        "OFFLINE"
                );
                mainHandler.post(() -> {
                    if (callback != null) callback.onLocationResolved(res);
                });
            }
        }).start();
    }

    private String formatStaleness(long timestamp) {
        if (timestamp <= 0) return "Never seen";
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) {
            return "Just now";
        } else if (diff < 3600000) {
            long mins = diff / 60000;
            return mins + " min" + (mins == 1 ? "" : "s") + " ago";
        } else if (diff < 86400000) {
            long hours = diff / 3600000;
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}
