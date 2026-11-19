package com.example.securetrack.repositories;

import android.content.Context;
import com.example.securetrack.models.*;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecurityRepository {

    private final FirebaseFirestore db;
    private final SessionManager sessionManager;

    public SecurityRepository(Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(context);
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private String getUid() {
        String uid = sessionManager.getFirebaseUid();
        if (uid == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return uid;
    }

    public void recordSecurityEvent(SecurityEvent event, ApiCallback<SecurityEvent> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        String eventId = UUID.randomUUID().toString();
        Map<String, Object> map = new HashMap<>();
        map.put("id", eventId);
        map.put("deviceId", event.getDeviceId() != null ? event.getDeviceId() : sessionManager.getCurrentDeviceId());
        map.put("deviceName", event.getDeviceName() != null ? event.getDeviceName() : sessionManager.getDeviceName());
        map.put("eventType", event.getEventType());
        map.put("severity", event.getSeverity());
        map.put("description", event.getDescription());
        if (event.getLocationStatus() != null) {
            map.put("locationStatus", event.getLocationStatus());
        }
        map.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("alerts").document(eventId)
                .set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> callback.onSuccess(event))
                .addOnFailureListener(e -> callback.onError("Firestore event error: " + e.getMessage()));
    }

    public void getUserSecurityEvents(ApiCallback<List<SecurityEvent>> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        db.collection("users").document(uid)
                .collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SecurityEvent> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String type = doc.getString("eventType");
                        String severity = doc.getString("severity");
                        String desc = doc.getString("description");
                        String devId = doc.getString("deviceId");
                        String devName = doc.getString("deviceName");
                        String locStatus = doc.getString("locationStatus");

                        SecurityEvent se = new SecurityEvent(
                                devId,
                                devName != null ? devName : "Android Device",
                                type != null ? type : "SECURITY_ALERT",
                                severity != null ? severity : "INFO",
                                desc != null ? desc : "Security event",
                                locStatus
                        );
                        se.setId(doc.getId());
                        list.add(se);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError("Firestore alert error: " + e.getMessage()));
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
                        list.add(cmd);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError("Firestore command history error: " + e.getMessage()));
    }

    public ListenerRegistration listenToPendingCommands(String deviceId, ApiCallback<List<DeviceCommand>> callback) {
        String uid = getUid();
        String targetDeviceId = deviceId != null ? deviceId : sessionManager.getPrimaryDeviceIdStr();
        if (uid == null || targetDeviceId == null) return null;

        return db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .collection("commands")
                .whereEqualTo("status", "QUEUED")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (querySnapshot != null) {
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
                            cmd.setId(doc.getId());
                            list.add(cmd);
                        }
                        callback.onSuccess(list);
                    }
                });
    }
}
