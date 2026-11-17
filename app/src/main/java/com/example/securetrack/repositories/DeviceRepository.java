package com.example.securetrack.repositories;

import android.content.Context;
import android.util.Log;
import com.example.securetrack.models.*;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceRepository {

    private static final String TAG = "DeviceRepository";

    private final FirebaseFirestore db;
    private final SessionManager sessionManager;

    public DeviceRepository(Context context) {
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

    public void registerDevice(Device device, ApiCallback<Device> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        String deviceId = device.getInstallationId() != null && !device.getInstallationId().isEmpty()
                ? device.getInstallationId()
                : String.valueOf(System.currentTimeMillis());

        Map<String, Object> map = new HashMap<>();
        map.put("id", deviceId);
        map.put("deviceName", device.getDeviceName());
        map.put("model", device.getModel());
        map.put("androidVersion", device.getAndroidVersion());
        map.put("batteryLevel", device.getBatteryLevel());
        map.put("status", device.getStatus() != null ? device.getStatus() : "ONLINE");
        map.put("protectionEnabled", device.isProtectionEnabled());
        map.put("installationId", device.getInstallationId());
        map.put("lastSeen", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    sessionManager.setPrimaryDeviceIdStr(deviceId);
                    try {
                        sessionManager.setPrimaryDeviceId(Long.parseLong(deviceId));
                    } catch (Exception ignored) {}

                    Device saved = new Device(
                            deviceId,
                            device.getDeviceName(),
                            device.getModel(),
                            device.getAndroidVersion(),
                            device.getBatteryLevel(),
                            "ONLINE",
                            device.isProtectionEnabled(),
                            device.getInstallationId()
                    );
                    callback.onSuccess(saved);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to register device", e);
                    callback.onError("Firestore error: " + e.getMessage());
                });
    }

    public void getUserDevices(ApiCallback<List<Device>> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        db.collection("users").document(uid)
                .collection("devices")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Device> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String id = doc.getString("id");
                        if (id == null) id = doc.getId();
                        String name = doc.getString("deviceName");
                        String model = doc.getString("model");
                        String androidVersion = doc.getString("androidVersion");
                        Long battery = doc.getLong("batteryLevel");
                        String status = doc.getString("status");
                        Boolean protection = doc.getBoolean("protectionEnabled");
                        String installId = doc.getString("installationId");

                        Device d = new Device(
                                id,
                                name != null ? name : "Android Device",
                                model != null ? model : "Generic",
                                androidVersion != null ? androidVersion : "14",
                                battery != null ? battery.intValue() : 100,
                                status != null ? status : "ONLINE",
                                protection != null && protection,
                                installId != null ? installId : id
                        );
                        list.add(d);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError("Firestore query error: " + e.getMessage()));
    }

    public ListenerRegistration listenToUserDevices(ApiCallback<List<Device>> callback) {
        String uid = getUid();
        if (uid == null) return null;

        return db.collection("users").document(uid)
                .collection("devices")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (querySnapshot != null) {
                        List<Device> list = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String id = doc.getString("id");
                            if (id == null) id = doc.getId();
                            String name = doc.getString("deviceName");
                            String model = doc.getString("model");
                            String androidVersion = doc.getString("androidVersion");
                            Long battery = doc.getLong("batteryLevel");
                            String status = doc.getString("status");
                            Boolean protection = doc.getBoolean("protectionEnabled");
                            String installId = doc.getString("installationId");

                            Device d = new Device(
                                    id,
                                    name != null ? name : "Android Device",
                                    model != null ? model : "Generic",
                                    androidVersion != null ? androidVersion : "14",
                                    battery != null ? battery.intValue() : 100,
                                    status != null ? status : "ONLINE",
                                    protection != null && protection,
                                    installId != null ? installId : id
                            );
                            list.add(d);
                        }
                        callback.onSuccess(list);
                    }
                });
    }

    public void createPairingCode(ApiCallback<PairingResponse> callback) {
        // Safe pairing requires a backend / Firebase Cloud Function to issue short-lived codes.
        callback.onError("Server-side pairing functions not deployed. Firebase Cloud Functions with billing enabled is required.");
    }

    public void completePairing(PairingRequest request, ApiCallback<Device> callback) {
        callback.onError("Server-side pairing functions not deployed.");
    }
}
