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
import com.google.firebase.functions.FirebaseFunctions;
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
        return sessionManager.getOwnerUid();
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

        FirebaseFunctions.getInstance()
                .getHttpsCallable("createDevicePairingCode")
                .call()
                .addOnSuccessListener(httpsCallableResult -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) httpsCallableResult.getData();
                    if (data != null && data.containsKey("code")) {
                        String code = String.valueOf(data.get("code"));
                        callback.onSuccess(new PairingResponse(code, 600));
                    } else {
                        callback.onError("Malformed response from pairing function.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create pairing code via Cloud Function", e);
                    callback.onError("Failed to create pairing code: " + e.getMessage());
                });
    }

    public void completePairing(PairingRequest request, ApiCallback<Device> callback) {
        String code = request.getCode() != null ? request.getCode().trim() : "";
        if (code.length() != 8) {
            callback.onError("Invalid pairing code. Code must be 8 digits.");
            return;
        }

        String installId = request.getInstallationId() != null ? request.getInstallationId() : sessionManager.getInstallationId();

        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("deviceName", request.getDeviceName());
        data.put("model", request.getModel());
        data.put("androidVersion", request.getAndroidVersion());
        data.put("installationId", installId);
        data.put("batteryLevel", DeviceUtils.getBatteryPercentage(context));

        FirebaseFunctions.getInstance()
                .getHttpsCallable("claimDevicePairingCode")
                .call(data)
                .addOnSuccessListener(httpsCallableResult -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> res = (Map<String, Object>) httpsCallableResult.getData();
                    if (res != null && Boolean.TRUE.equals(res.get("success"))) {
                        String ownerUid = (String) res.get("ownerUid");
                        String deviceId = (String) res.get("deviceId");
                        String customToken = (String) res.get("customToken");

                        // Sign in with the device-scoped custom token issued by Firebase Admin
                        if (customToken != null && !customToken.isEmpty()) {
                            FirebaseAuth.getInstance().signInWithCustomToken(customToken)
                                    .addOnSuccessListener(authResult -> {
                                        com.google.firebase.auth.FirebaseUser user = authResult.getUser();
                                        if (user == null) {
                                            callback.onError("Authentication failed: null user after custom token sign in");
                                            return;
                                        }

                                        // Force token refresh and verify cryptographic custom claims
                                        user.getIdToken(true).addOnSuccessListener(tokenResult -> {
                                            Map<String, Object> claims = tokenResult.getClaims();
                                            String roleClaim = claims != null ? (String) claims.get("role") : null;
                                            String ownerUidClaim = claims != null ? (String) claims.get("ownerUid") : null;
                                            String deviceIdClaim = claims != null ? (String) claims.get("deviceId") : null;
                                            Boolean revokedClaim = claims != null ? (Boolean) claims.get("revoked") : null;

                                            // Fail closed if claims do not match the expected owner and device
                                            if (!"device".equals(roleClaim) ||
                                                    ownerUidClaim == null || !ownerUidClaim.equals(ownerUid) ||
                                                    deviceIdClaim == null || !deviceIdClaim.equals(deviceId) ||
                                                    Boolean.TRUE.equals(revokedClaim)) {

                                                Log.e(TAG, "Security claims mismatch! Claims: " + claims);
                                                FirebaseAuth.getInstance().signOut();
                                                sessionManager.logout();
                                                callback.onError("Security violation: Token claims do not match pairing authorization");
                                                return;
                                            }

                                            sessionManager.setOwnerUid(ownerUid);
                                            sessionManager.setCurrentDeviceId(deviceId);
                                            sessionManager.setDeviceName(request.getDeviceName());

                                            Device enrolledDevice = new Device(
                                                    deviceId,
                                                    request.getDeviceName(),
                                                    request.getModel(),
                                                    request.getAndroidVersion(),
                                                    DeviceUtils.getBatteryPercentage(context),
                                                    "ONLINE",
                                                    true,
                                                    installId
                                            );
                                            callback.onSuccess(enrolledDevice);
                                        }).addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to retrieve and verify ID token claims", e);
                                            FirebaseAuth.getInstance().signOut();
                                            sessionManager.logout();
                                            callback.onError("Failed to verify token claims: " + e.getMessage());
                                        });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to sign in with device custom token", e);
                                        callback.onError("Failed to authenticate device credentials: " + e.getMessage());
                                    });
                        } else {
                            callback.onError("Security violation: Missing custom token from pairing response.");
                        }
                    } else {
                        callback.onError("Pairing failed: invalid response from server.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cloud function claimDevicePairingCode failed", e);
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Pairing failed");
                });
    }

    public void revokePairedDevice(String deviceId, ApiCallback<Void> callback) {
        if (deviceId == null || deviceId.isEmpty()) {
            if (callback != null) callback.onError("Invalid device ID");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("revokePairedDevice")
                .call(data)
                .addOnSuccessListener(httpsCallableResult -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Revoke paired device failed", e);
                    if (callback != null) callback.onError(e.getMessage());
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
