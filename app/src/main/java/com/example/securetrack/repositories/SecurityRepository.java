package com.example.securetrack.repositories;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.SecurityAlertEntity;
import com.example.securetrack.models.*;
import com.example.securetrack.utils.SessionManager;
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

public class SecurityRepository {

    private static final String TAG = "SecurityRepository";
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final AppDatabase appDatabase;

    public SecurityRepository(Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(context);
        this.appDatabase = AppDatabase.getInstance(context);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private String getUid() {
        return sessionManager.getOwnerUid();
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Long> recentEventDebounce = new java.util.concurrent.ConcurrentHashMap<>();

    public LiveData<Integer> getActiveAlertCountLiveData() {
        return appDatabase.securityAlertDao().getActiveAlertCountLiveData();
    }

    public LiveData<Integer> getDismissedAlertCountLiveData() {
        return appDatabase.securityAlertDao().getDismissedAlertCountLiveData();
    }

    public LiveData<List<SecurityAlertEntity>> getActiveAlertsLiveData() {
        return appDatabase.securityAlertDao().getActiveAlertsLiveData();
    }

    public LiveData<List<SecurityAlertEntity>> getAllAlertsLiveData() {
        return appDatabase.securityAlertDao().getAllAlertsLiveData();
    }

    public void recordSecurityEvent(SecurityEvent event, ApiCallback<SecurityEvent> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        // Duplicate-alert prevention: debounce same device & eventType within 30 seconds
        String debounceKey = (event.getDeviceId() != null ? event.getDeviceId() : "device") + ":" + event.getEventType();
        long now = System.currentTimeMillis();
        Long lastTime = recentEventDebounce.get(debounceKey);
        if (lastTime != null && (now - lastTime < 30000L)) {
            Log.i(TAG, "Suppressed duplicate security alert: " + debounceKey);
            callback.onSuccess(event);
            return;
        }
        recentEventDebounce.put(debounceKey, now);

        String eventId = UUID.randomUUID().toString();
        Map<String, Object> map = new HashMap<>();
        map.put("id", eventId);
        map.put("deviceId", event.getDeviceId());
        map.put("deviceName", event.getDeviceName());
        map.put("eventType", event.getEventType());
        map.put("severity", event.getSeverity());
        map.put("description", event.getDescription());
        map.put("status", "ACTIVE");
        if (event.getLocationStatus() != null) {
            map.put("locationStatus", event.getLocationStatus());
        }
        map.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("alerts").document(eventId)
                .set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Record locally in Room
                    new Thread(() -> {
                        SecurityAlertEntity entity = new SecurityAlertEntity(
                                eventId,
                                event.getDeviceId(),
                                event.getDeviceName(),
                                event.getEventType(),
                                event.getSeverity(),
                                event.getDescription(),
                                event.getLocationStatus(),
                                "ACTIVE",
                                System.currentTimeMillis(),
                                0, null, 0, true
                        );
                        appDatabase.securityAlertDao().insertOrUpdate(entity);
                    }).start();
                    callback.onSuccess(event);
                })
                .addOnFailureListener(e -> callback.onError("Firestore event error: " + e.getMessage()));
    }

    public void getUserSecurityEvents(ApiCallback<List<SecurityEvent>> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        String firebaseUid = sessionManager.getFirebaseUid();
        boolean isSecondary = firebaseUid != null && !firebaseUid.equals(uid);
        if (isSecondary) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        db.collection("users").document(uid)
                .collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SecurityEvent> list = new ArrayList<>();
                    List<SecurityAlertEntity> entities = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String type = doc.getString("eventType");
                        String severity = doc.getString("severity");
                        String desc = doc.getString("description");
                        String devId = doc.getString("deviceId");
                        String devName = doc.getString("deviceName");
                        String locStatus = doc.getString("locationStatus");
                        String status = doc.getString("status");
                        Long dismissedAt = doc.getLong("dismissedAt");
                        String dismissedBy = doc.getString("dismissedBy");
                        Long resolvedAt = doc.getLong("resolvedAt");

                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                        long time = ts != null ? ts.toDate().getTime() : System.currentTimeMillis();

                        SecurityEvent se = new SecurityEvent(
                                devId,
                                devName != null ? devName : "Android Device",
                                type != null ? type : "SECURITY_ALERT",
                                severity != null ? severity : "INFO",
                                desc != null ? desc : "Security event",
                                locStatus
                        );
                        se.setId(doc.getId());
                        se.setStatus(status != null ? status : "ACTIVE");
                        se.setDismissedAt(dismissedAt != null ? dismissedAt : 0);
                        se.setDismissedBy(dismissedBy);
                        se.setResolvedAt(resolvedAt != null ? resolvedAt : 0);
                        list.add(se);

                        entities.add(new SecurityAlertEntity(
                                doc.getId(),
                                devId,
                                devName,
                                type,
                                severity,
                                desc,
                                locStatus,
                                status != null ? status : "ACTIVE",
                                time,
                                dismissedAt != null ? dismissedAt : 0,
                                dismissedBy,
                                resolvedAt != null ? resolvedAt : 0,
                                true
                        ));
                    }

                    // Cache in Room
                    new Thread(() -> appDatabase.securityAlertDao().insertAll(entities)).start();
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    // Fallback to local Room cache when offline
                    new Thread(() -> {
                        List<SecurityAlertEntity> cached = appDatabase.securityAlertDao().getActiveAlertsSync();
                        List<SecurityEvent> fallbackList = new ArrayList<>();
                        for (SecurityAlertEntity ent : cached) {
                            SecurityEvent se = new SecurityEvent(
                                    ent.getDeviceId(),
                                    ent.getDeviceName(),
                                    ent.getEventType(),
                                    ent.getSeverity(),
                                    ent.getDescription(),
                                    ent.getLocationStatus()
                            );
                            se.setId(ent.getId());
                            se.setStatus(ent.getStatus());
                            se.setDismissedAt(ent.getDismissedAt());
                            se.setDismissedBy(ent.getDismissedBy());
                            se.setResolvedAt(ent.getResolvedAt());
                            fallbackList.add(se);
                        }
                        callback.onSuccess(fallbackList);
                    }).start();
                });
    }

    public void dismissAlert(String alertId, ApiCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "DISMISSED");
        updates.put("dismissedAt", now);
        updates.put("dismissedBy", uid);

        // Update local Room database immediately (optimistic UI)
        new Thread(() -> appDatabase.securityAlertDao().dismissAlert(alertId, now, uid)).start();

        // Update Firestore
        db.collection("users").document(uid)
                .collection("alerts").document(alertId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    new Thread(() -> appDatabase.securityAlertDao().markAlertSynced(alertId)).start();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError("Failed to sync dismissal to cloud: " + e.getMessage()));
    }

    public void restoreAlert(String alertId, ApiCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "ACTIVE");
        updates.put("dismissedAt", FieldValue.delete());
        updates.put("dismissedBy", FieldValue.delete());

        // Update local Room database immediately (undo support)
        new Thread(() -> appDatabase.securityAlertDao().restoreAlert(alertId)).start();

        // Update Firestore
        db.collection("users").document(uid)
                .collection("alerts").document(alertId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    new Thread(() -> appDatabase.securityAlertDao().markAlertSynced(alertId)).start();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError("Failed to restore alert in cloud: " + e.getMessage()));
    }

    public void resolveAlert(String alertId, ApiCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "RESOLVED");
        updates.put("resolvedAt", now);

        new Thread(() -> appDatabase.securityAlertDao().resolveAlert(alertId, now)).start();

        db.collection("users").document(uid)
                .collection("alerts").document(alertId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    new Thread(() -> appDatabase.securityAlertDao().markAlertSynced(alertId)).start();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> callback.onError("Failed to resolve alert: " + e.getMessage()));
    }

    public void bulkDismissResolvedAlerts(ApiCallback<Integer> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        long now = System.currentTimeMillis();
        new Thread(() -> {
            int dismissedCount = appDatabase.securityAlertDao().bulkDismissResolved(now, uid);
            // Also update in Firestore
            db.collection("users").document(uid)
                    .collection("alerts")
                    .whereIn("status", java.util.Arrays.asList("RESOLVED", "INFO"))
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("status", "DISMISSED");
                            updates.put("dismissedAt", now);
                            updates.put("dismissedBy", uid);
                            doc.getReference().set(updates, SetOptions.merge());
                        }
                        callback.onSuccess(dismissedCount);
                    })
                    .addOnFailureListener(e -> callback.onSuccess(dismissedCount));
        }).start();
    }

    public void createCommand(String deviceId, DeviceCommand command, ApiCallback<DeviceCommand> callback) {
        String uid = getUid();
        String targetDeviceId = deviceId != null ? deviceId : sessionManager.getPrimaryDeviceIdStr();
        if (uid == null || targetDeviceId == null) {
            callback.onError("User or device not identified");
            return;
        }

        String commandId = UUID.randomUUID().toString();
        Map<String, Object> map = new HashMap<>();
        map.put("id", commandId);
        map.put("commandType", command.getCommandType());
        map.put("command", command.getCommandType());
        map.put("targetDeviceId", targetDeviceId);
        map.put("status", "QUEUED");
        map.put("payload", command.getPayload());
        map.put("result", command.getResult());
        map.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .collection("commands").document(commandId)
                .set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    DeviceCommand created = new DeviceCommand(
                            command.getCommandType(),
                            "QUEUED",
                            command.getPayload(),
                            command.getResult()
                    );
                    created.setId(commandId);
                    created.setDeviceId(targetDeviceId);
                    callback.onSuccess(created);
                })
                .addOnFailureListener(e -> callback.onError("Firestore command dispatch error: " + e.getMessage()));
    }

    public void getDeviceCommands(String deviceId, ApiCallback<List<DeviceCommand>> callback) {
        String uid = getUid();
        String targetDeviceId = deviceId != null ? deviceId : sessionManager.getPrimaryDeviceIdStr();
        if (uid == null || targetDeviceId == null) {
            callback.onError("User or device not identified");
            return;
        }

        db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .collection("commands")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DeviceCommand> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String type = doc.getString("commandType");
                        String status = doc.getString("status");
                        String payload = doc.getString("payload");
                        String result = doc.getString("result");

                        DeviceCommand cmd = new DeviceCommand(
                                type != null ? type : "COMMAND",
                                status != null ? status : "QUEUED",
                                payload,
                                result
                        );
                        com.google.firebase.Timestamp ts = doc.getTimestamp("createdAt");
                        if (ts != null) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault());
                            cmd.setCreatedAt(sdf.format(ts.toDate()));
                        }
                        list.add(cmd);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError("Firestore command history error: " + e.getMessage()));
    }
}
