package com.example.securetrack.repositories;

import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import com.example.securetrack.models.*;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceRepository {

    private static final String TAG = "DeviceRepository";

    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final Context context;

    public DeviceRepository(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(this.context);
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

    public void registerDevice(Device device, ApiCallback<Device> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        // 1:1 persistent relationship: deviceId is this phone's persistent installationId
        String deviceId = device.getInstallationId() != null && !device.getInstallationId().isEmpty()
                ? device.getInstallationId()
                : sessionManager.getInstallationId();

        Map<String, Object> map = new HashMap<>();
        map.put("id", deviceId);
        map.put("deviceName", device.getDeviceName());
        map.put("model", device.getModel());
        map.put("androidVersion", device.getAndroidVersion());
        map.put("batteryLevel", device.getBatteryLevel());
        map.put("status", device.getStatus() != null ? device.getStatus() : "ONLINE");
        map.put("protectionEnabled", device.isProtectionEnabled());
        map.put("installationId", device.getInstallationId());
        map.put("ownerUid", uid);
        map.put("locationStatus", "ENABLED");
        map.put("trackingStatus", "ACTIVE");
        map.put("networkStatus", "CONNECTED");
        map.put("lastSeen", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .set(map, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (device.getImei1() != null || device.getImei2() != null) {
                        Map<String, Object> recoveryMap = new HashMap<>();
                        if (device.getImei1() != null) recoveryMap.put("imei1", device.getImei1());
                        if (device.getImei2() != null) recoveryMap.put("imei2", device.getImei2());
                        recoveryMap.put("consentGiven", true);
                        recoveryMap.put("timestamp", FieldValue.serverTimestamp());

                        db.collection("users").document(uid)
                                .collection("private_recovery").document(deviceId)
                                .set(recoveryMap, SetOptions.merge());
                    }

                    sessionManager.setCurrentDeviceId(deviceId);
                    sessionManager.setDeviceName(device.getDeviceName());
                    sessionManager.setOwnerUid(uid);

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
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        SecureRandom random = new SecureRandom();
        int codeInt = 10000000 + random.nextInt(90000000);
        String codeStr = String.valueOf(codeInt);
        long expiresAtMillis = System.currentTimeMillis() + 10 * 60 * 1000;

        Map<String, Object> map = new HashMap<>();
        map.put("code", codeStr);
        map.put("ownerUid", uid);
        map.put("status", "ACTIVE");
        map.put("createdAt", FieldValue.serverTimestamp());
        map.put("expiresAt", new Timestamp(new Date(expiresAtMillis)));
        map.put("attempts", 0);

        db.collection("pairing_codes").document(codeStr)
                .set(map)
                .addOnSuccessListener(aVoid -> callback.onSuccess(new PairingResponse(codeStr, 600)))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create pairing code", e);
                    callback.onError("Failed to create pairing code: " + e.getMessage());
                });
    }

    public void completePairing(PairingRequest request, ApiCallback<Device> callback) {
        String code = request.getCode() != null ? request.getCode().trim() : "";
        if (code.length() != 8) {
            callback.onError("Invalid pairing code. Code must be 8 digits.");
            return;
        }

        db.runTransaction(transaction -> {
            DocumentSnapshot codeDoc = transaction.get(db.collection("pairing_codes").document(code));
            if (!codeDoc.exists()) {
                throw new IllegalStateException("Invalid pairing code.");
            }

            String status = codeDoc.getString("status");
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Pairing code has already been redeemed or is inactive.");
            }

            Timestamp expiresAt = codeDoc.getTimestamp("expiresAt");
            if (expiresAt != null && expiresAt.toDate().before(new Date())) {
                transaction.update(db.collection("pairing_codes").document(code), "status", "EXPIRED");
                throw new IllegalStateException("Pairing code has expired.");
            }

            String ownerUid = codeDoc.getString("ownerUid");
            if (ownerUid == null || ownerUid.isEmpty()) {
                throw new IllegalStateException("Corrupt pairing code record.");
            }

            String deviceId = request.getInstallationId() != null ? request.getInstallationId() : sessionManager.getInstallationId();

            // Mark pairing code as CLAIMED atomically
            Map<String, Object> codeUpdates = new HashMap<>();
            codeUpdates.put("status", "CLAIMED");
            codeUpdates.put("claimedAt", FieldValue.serverTimestamp());
            codeUpdates.put("claimedDeviceId", deviceId);
            transaction.update(db.collection("pairing_codes").document(code), codeUpdates);

            // Register device under the owner UID
            Map<String, Object> deviceMap = new HashMap<>();
            deviceMap.put("id", deviceId);
            deviceMap.put("deviceName", request.getDeviceName());
            deviceMap.put("model", request.getModel());
            deviceMap.put("androidVersion", request.getAndroidVersion());
            deviceMap.put("batteryLevel", DeviceUtils.getBatteryPercentage(context));
            deviceMap.put("status", "ONLINE");
            deviceMap.put("protectionEnabled", true);
            deviceMap.put("installationId", request.getInstallationId());
            deviceMap.put("paired", true);
            deviceMap.put("ownerUid", ownerUid);
            deviceMap.put("locationStatus", "ENABLED");
            deviceMap.put("trackingStatus", "ACTIVE");
            deviceMap.put("lastSeen", FieldValue.serverTimestamp());

            transaction.set(db.collection("users").document(ownerUid)
                    .collection("devices").document(deviceId), deviceMap, SetOptions.merge());

            return new Device(
                    deviceId,
                    request.getDeviceName(),
                    request.getModel(),
                    request.getAndroidVersion(),
                    DeviceUtils.getBatteryPercentage(context),
                    "ONLINE",
                    true,
                    request.getInstallationId()
            );
        }).addOnSuccessListener(device -> {
            sessionManager.setCurrentDeviceId(device.getId());
            sessionManager.setDeviceName(device.getDeviceName());
            callback.onSuccess(device);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Transaction failed for pairing code claim", e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Pairing failed");
        });
    }

    public void sendHeartbeat(ApiCallback<Void> callback) {
        String uid = getUid();
        String deviceId = sessionManager.getCurrentDeviceId();
        if (uid == null || deviceId == null) {
            if (callback != null) callback.onError("No active device session");
            return;
        }

        boolean isLocEnabled = false;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLocEnabled = lm.isLocationEnabled();
            } else {
                isLocEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastSeen", FieldValue.serverTimestamp());
        updates.put("batteryLevel", DeviceUtils.getBatteryPercentage(context));
        updates.put("locationStatus", isLocEnabled ? "ENABLED" : "DISABLED");
        updates.put("trackingStatus", sessionManager.isBackgroundTrackingEnabled() ? "ACTIVE" : "STOPPED");
        updates.put("status", "ONLINE");

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
    }
}
