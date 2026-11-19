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
        String uid = sessionManager.getOwnerUid();
        if (uid == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return uid;
    }

    public void recordLocation(LocationRecord record, ApiCallback<LocationRecord> callback) {
        String uid = getUid();
        String deviceId = record.getDeviceId() != null ? record.getDeviceId() : sessionManager.getCurrentDeviceId();

        if (uid == null || deviceId == null) {
            cacheLocally(record);
            callback.onError("User or device not identified, cached locally");
            return;
        }

        String locationId = record.getClientUuid() != null && !record.getClientUuid().isEmpty()
                ? record.getClientUuid()
                : UUID.randomUUID().toString();

        Map<String, Object> map = new HashMap<>();
        map.put("clientUuid", locationId);
        map.put("latitude", record.getLatitude());
        map.put("longitude", record.getLongitude());
        map.put("accuracy", record.getAccuracy());
        map.put("altitude", record.getAltitude());
        map.put("speed", record.getSpeed());
        map.put("source", "GPS");
        map.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("locations").document(locationId)
                .set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Update device lastSeen, location source, and telemetry
                    Map<String, Object> deviceUpdate = new HashMap<>();
                    deviceUpdate.put("lastSeen", FieldValue.serverTimestamp());
                    deviceUpdate.put("lastLatitude", record.getLatitude());
                    deviceUpdate.put("lastLongitude", record.getLongitude());
                    deviceUpdate.put("locationAccuracy", record.getAccuracy());
                    deviceUpdate.put("locationSource", "GPS");
                    deviceUpdate.put("locationStatus", "ENABLED");
                    deviceUpdate.put("trackingStatus", "ACTIVE");
                    deviceUpdate.put("locationTimestamp", FieldValue.serverTimestamp());

                    db.collection("users").document(uid)
                            .collection("devices").document(deviceId)
                            .set(deviceUpdate, SetOptions.merge());

                    callback.onSuccess(record);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore location write error", e);
                    cacheLocally(record);
                    callback.onError("Firestore offline, saved locally");
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

    private void cacheLocally(LocationRecord record) {
        executor.execute(() -> {
            if (record.getDeviceId() != null) {
                LocationEntity entity = new LocationEntity(
                        record.getDeviceId(),
                        record.getClientUuid() != null ? record.getClientUuid() : UUID.randomUUID().toString(),
                        record.getLatitude(),
                        record.getLongitude(),
                        record.getAccuracy() != null ? record.getAccuracy() : 0.0f,
                        record.getAltitude() != null ? record.getAltitude() : 0.0,
                        record.getSpeed() != null ? record.getSpeed() : 0.0f,
                        record.getTimestamp(),
                        false
                );
                locationDao.insert(entity);
            }
        });
    }
}
