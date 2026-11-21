package com.example.securetrack.repositories;

import android.content.Context;
import android.util.Log;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.LocationDao;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationRepository {

    private static final String TAG = "LocationRepository";

    private final FirebaseFirestore db;
    private final LocationDao locationDao;
    private final SessionManager sessionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LocationRepository(Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.locationDao = AppDatabase.getInstance(context).locationDao();
        this.sessionManager = new SessionManager(context);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private String getUid() {
        return sessionManager.getOwnerUid();
    }

    public static com.google.firebase.Timestamp parseTimestamp(String timestampStr) {
        return com.example.securetrack.utils.TimestampUtils.parseTimestamp(timestampStr);
    }

    public void recordLocation(LocationRecord record, ApiCallback<LocationRecord> callback) {
        String uid = getUid();
        String deviceId = record.getDeviceId() != null ? record.getDeviceId() : sessionManager.getCurrentDeviceId();

        final String locationId = record.getClientUuid() != null && !record.getClientUuid().isEmpty()
                ? record.getClientUuid()
                : UUID.randomUUID().toString();
        record.setClientUuid(locationId);

        // 1. Write-ahead durable persistence in Room DB before network transmission
        executor.execute(() -> {
            long localRowId = -1;
            if (deviceId != null) {
                try {
                    LocationEntity existing = locationDao.getByClientUuid(locationId);
                    if (existing != null) {
                        localRowId = existing.getId();
                    } else {
                        LocationEntity entity = new LocationEntity(
                                deviceId,
                                locationId,
                                record.getLatitude(),
                                record.getLongitude(),
                                record.getAccuracy() != null ? record.getAccuracy() : 0.0f,
                                record.getAltitude() != null ? record.getAltitude() : 0.0,
                                record.getSpeed() != null ? record.getSpeed() : 0.0f,
                                record.getTimestamp(),
                                false
                        );
                        localRowId = locationDao.insert(entity);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Local Room database insertion failed for " + locationId, e);
                    localRowId = -1;
                }
            }
            final long insertedId = localRowId;

            if (insertedId <= 0) {
                Log.e(TAG, "Local database insertion failed for location " + locationId);
                if (callback != null) callback.onError("Local database insertion failed: cloud persistence aborted");
                return;
            }

            // 2. If unauthenticated, exit after confirming durable local storage
            if (uid == null || deviceId == null) {
                if (callback != null) callback.onError("User or device not identified, stored durably locally");
                return;
            }

            // 3. Attempt cloud transmission
            com.google.firebase.Timestamp gpsTimestamp = parseTimestamp(record.getTimestamp());

            Map<String, Object> map = new HashMap<>();
            map.put("clientUuid", locationId);
            map.put("requestId", locationId);
            map.put("latitude", record.getLatitude());
            map.put("longitude", record.getLongitude());
            map.put("accuracy", record.getAccuracy());
            map.put("altitude", record.getAltitude());
            map.put("speed", record.getSpeed());
            map.put("source", "GPS");
            map.put("timestamp", gpsTimestamp);
            map.put("uploadedAt", FieldValue.serverTimestamp());

            db.collection("users").document(uid)
                    .collection("devices").document(deviceId)
                    .collection("locations").document(locationId)
                    .set(map, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        // Confirmed successful cloud upload: mark as synced
                        if (insertedId > 0) {
                            executor.execute(() -> locationDao.markAsSynced(insertedId));
                        }

                        // Update device telemetry
                        Map<String, Object> deviceUpdate = new HashMap<>();
                        deviceUpdate.put("lastSeen", FieldValue.serverTimestamp());
                        deviceUpdate.put("lastLatitude", record.getLatitude());
                        deviceUpdate.put("lastLongitude", record.getLongitude());
                        deviceUpdate.put("locationAccuracy", record.getAccuracy());
                        deviceUpdate.put("locationSource", "GPS");
                        deviceUpdate.put("locationStatus", "ENABLED");
                        deviceUpdate.put("trackingStatus", "ACTIVE");
                        deviceUpdate.put("locationTimestamp", gpsTimestamp);

                        db.collection("users").document(uid)
                                .collection("devices").document(deviceId)
                                .set(deviceUpdate, SetOptions.merge());

                        if (callback != null) callback.onSuccess(record);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Cloud write failed; location is durably queued in local Room DB: " + e.getMessage());
                        if (callback != null) callback.onError("Cloud write failed; queued locally for background retry");
                    });
        });
    }

    public void getLocationHistoryStr(String deviceId, ApiCallback<List<LocationRecord>> callback) {
        String uid = getUid();
        if (uid == null || deviceId == null) {
            callback.onError("User or device not identified");
            return;
        }

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("locations")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<LocationRecord> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        Double accuracyD = doc.getDouble("accuracy");
                        Double altitude = doc.getDouble("altitude");
                        Double speedD = doc.getDouble("speed");
                        String clientUuid = doc.getString("clientUuid");

                        if (lat != null && lng != null) {
                            LocationRecord lr = new LocationRecord(
                                    deviceId,
                                    clientUuid != null ? clientUuid : doc.getId(),
                                    lat,
                                    lng,
                                    accuracyD != null ? accuracyD.floatValue() : 0.0f,
                                    altitude != null ? altitude : 0.0,
                                    speedD != null ? speedD.floatValue() : 0.0f,
                                    doc.getTimestamp("timestamp") != null ? doc.getTimestamp("timestamp").toDate().toString() : ""
                            );
                            list.add(lr);
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError("Firestore location history error: " + e.getMessage()));
    }

    public void getLocationHistory(String deviceId, ApiCallback<List<LocationRecord>> callback) {
        String devIdStr = deviceId != null ? deviceId : sessionManager.getPrimaryDeviceIdStr();
        getLocationHistoryStr(devIdStr, callback);
    }

    public com.google.firebase.firestore.ListenerRegistration listenToLatestLocation(String deviceId, ApiCallback<LocationRecord> callback) {
        String uid = getUid();
        if (uid == null || deviceId == null) return null;

        return db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("locations")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        Double accuracyD = doc.getDouble("accuracy");
                        Double altitude = doc.getDouble("altitude");
                        Double speedD = doc.getDouble("speed");
                        String clientUuid = doc.getString("clientUuid");

                        if (lat != null && lng != null) {
                            LocationRecord lr = new LocationRecord(
                                    deviceId,
                                    clientUuid != null ? clientUuid : doc.getId(),
                                    lat,
                                    lng,
                                    accuracyD != null ? accuracyD.floatValue() : 0.0f,
                                    altitude != null ? altitude : 0.0,
                                    speedD != null ? speedD.floatValue() : 0.0f,
                                    doc.getTimestamp("timestamp") != null ? doc.getTimestamp("timestamp").toDate().toString() : ""
                            );
                            callback.onSuccess(lr);
                        }
                    } else {
                        callback.onError("No location record available");
                    }
                });
    }
}
