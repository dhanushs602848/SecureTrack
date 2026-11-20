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
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.functions.FirebaseFunctions;
import java.security.MessageDigest;
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

        String firebaseUid = sessionManager.getFirebaseUid();
        boolean isSecondary = firebaseUid != null && !firebaseUid.equals(uid);

        if (isSecondary) {
            String currentDeviceId = sessionManager.getCurrentDeviceId();
            if (currentDeviceId == null) {
                currentDeviceId = sessionManager.getInstallationId();
            }

            db.collection("users").document(uid)
                    .collection("devices").document(currentDeviceId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        List<Device> list = new ArrayList<>();
                        if (doc.exists()) {
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

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.trim().getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Spark-Compatible: Owner generates a 256-bit cryptographic one-time pairing invitation.
     */
    public void createPairingInvitation(ApiCallback<PairingInvitation> callback) {
        String uid = getUid();
        if (uid == null) {
            callback.onError("User not authenticated");
            return;
        }

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        String rawToken = sb.toString();
        String tokenHash = hashToken(rawToken);

        Date expiresAt = new Date(System.currentTimeMillis() + 10 * 60 * 1000);

        Map<String, Object> map = new HashMap<>();
        map.put("ownerUid", uid);
        map.put("ownerEmail", sessionManager.getUserEmail());
        map.put("status", "PENDING_CLAIM");
        map.put("createdAt", FieldValue.serverTimestamp());
        map.put("expiresAt", new Timestamp(expiresAt));
        map.put("claimedByUid", null);
        map.put("claimedDeviceId", null);
        map.put("deviceInfo", null);
        map.put("approvedAt", null);

        String qrPayload = "{\"scheme\":\"securetrack\",\"action\":\"pair\",\"token\":\"" + rawToken + "\",\"ownerUid\":\"" + uid + "\"}";

        db.collection("pairing_invitations").document(tokenHash)
                .set(map)
                .addOnSuccessListener(aVoid -> {
                    PairingInvitation inv = new PairingInvitation(
                            rawToken,
                            tokenHash,
                            uid,
                            sessionManager.getUserEmail(),
                            "PENDING_CLAIM",
                            expiresAt,
                            qrPayload
                    );
                    callback.onSuccess(inv);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create pairing invitation", e);
                    callback.onError("Failed to create pairing invitation: " + e.getMessage());
                });
    }

    /**
     * Real-time listener for owner to monitor status of an invitation.
     */
    public ListenerRegistration listenToInvitation(String tokenHash, ApiCallback<PairingInvitation> callback) {
        return db.collection("pairing_invitations").document(tokenHash)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        PairingInvitation inv = new PairingInvitation();
                        inv.setTokenHash(tokenHash);
                        inv.setOwnerUid(snapshot.getString("ownerUid"));
                        inv.setOwnerEmail(snapshot.getString("ownerEmail"));
                        inv.setStatus(snapshot.getString("status"));
                        Timestamp exp = snapshot.getTimestamp("expiresAt");
                        if (exp != null) inv.setExpiresAt(exp.toDate());
                        inv.setClaimedByUid(snapshot.getString("claimedByUid"));
                        inv.setClaimedDeviceId(snapshot.getString("claimedDeviceId"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> devInfo = (Map<String, Object>) snapshot.get("deviceInfo");
                        inv.setDeviceInfo(devInfo);
                        callback.onSuccess(inv);
                    }
                });
    }

    /**
     * Owner approves an enrolling secondary device.
     */
    public void approveDeviceClaim(PairingInvitation invitation, ApiCallback<Void> callback) {
        String uid = getUid();
        if (uid == null || !uid.equals(invitation.getOwnerUid())) {
            callback.onError("Unauthorized");
            return;
        }

        String deviceId = invitation.getClaimedDeviceId();
        Map<String, Object> devInfo = invitation.getDeviceInfo();

        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("id", deviceId);
        deviceMap.put("ownerUid", uid);
        deviceMap.put("deviceAuthUid", invitation.getClaimedByUid());
        deviceMap.put("deviceName", devInfo != null && devInfo.get("deviceName") != null ? devInfo.get("deviceName") : "Paired Android Device");
        deviceMap.put("model", devInfo != null && devInfo.get("model") != null ? devInfo.get("model") : "Android");
        deviceMap.put("androidVersion", devInfo != null && devInfo.get("androidVersion") != null ? devInfo.get("androidVersion") : "14");
        deviceMap.put("batteryLevel", devInfo != null && devInfo.get("batteryLevel") instanceof Number ? ((Number) devInfo.get("batteryLevel")).intValue() : 100);
        deviceMap.put("status", "ONLINE");
        deviceMap.put("paired", true);
        deviceMap.put("protectionEnabled", true);
        deviceMap.put("installationId", deviceId);
        deviceMap.put("lastSeen", FieldValue.serverTimestamp());
        deviceMap.put("locationStatus", "ENABLED");
        deviceMap.put("trackingStatus", "ACTIVE");
        deviceMap.put("networkStatus", "CONNECTED");

        // Generate cryptographically secure 256-bit high-entropy BLE shared secret
        byte[] secretBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(secretBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : secretBytes) {
            sb.append(String.format("%02x", b));
        }
        String bleSharedSecret = sb.toString();
        deviceMap.put("bleSharedSecret", bleSharedSecret);
        sessionManager.saveBleDeviceSecret(deviceId, bleSharedSecret);

        WriteBatch batch = db.batch();
        DocumentReference devRef = db.collection("users").document(uid).collection("devices").document(deviceId);
        batch.set(devRef, deviceMap, SetOptions.merge());

        DocumentReference invRef = db.collection("pairing_invitations").document(invitation.getTokenHash());
        Map<String, Object> invUpdate = new HashMap<>();
        invUpdate.put("status", "APPROVED");
        invUpdate.put("approvedAt", FieldValue.serverTimestamp());
        invUpdate.put("bleSharedSecret", bleSharedSecret);
        batch.update(invRef, invUpdate);

        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to approve device claim", e);
                    callback.onError("Failed to approve device: " + e.getMessage());
                });
    }

    /**
     * Owner rejects an enrolling secondary device.
     */
    public void rejectDeviceClaim(String tokenHash, ApiCallback<Void> callback) {
        db.collection("pairing_invitations").document(tokenHash)
                .update("status", "REJECTED")
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("Failed to reject device: " + e.getMessage()));
    }

    /**
     * Secondary enrolling device claims a pairing invitation.
     */
    public void claimPairingInvitation(String rawToken, String deviceName, ApiCallback<Device> callback) {
        if (rawToken == null || rawToken.trim().length() < 32) {
            callback.onError("Invalid pairing token. Token must be at least 32 characters.");
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnSuccessListener(authResult -> executeClaim(authResult.getUser().getUid(), rawToken.trim(), deviceName, callback))
                    .addOnFailureListener(e -> callback.onError("Failed device authentication: " + e.getMessage()));
        } else {
            executeClaim(auth.getCurrentUser().getUid(), rawToken.trim(), deviceName, callback);
        }
    }

    private void executeClaim(String deviceAuthUid, String rawToken, String deviceName, ApiCallback<Device> callback) {
        String tokenHash = hashToken(rawToken);
        String installId = sessionManager.getInstallationId();
        DocumentReference invRef = db.collection("pairing_invitations").document(tokenHash);

        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(invRef);
            if (!snap.exists()) {
                throw new FirebaseFirestoreException("Invalid or unrecognized pairing token.", FirebaseFirestoreException.Code.NOT_FOUND);
            }
            String status = snap.getString("status");
            if (!"PENDING_CLAIM".equals(status)) {
                throw new FirebaseFirestoreException("Invitation is " + (status != null ? status.toLowerCase() : "invalid") + " and cannot be reused.", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            Timestamp exp = snap.getTimestamp("expiresAt");
            if (exp != null && exp.toDate().before(new Date())) {
                throw new FirebaseFirestoreException("Pairing invitation has expired.", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED);
            }

            Map<String, Object> update = new HashMap<>();
            update.put("status", "PENDING_APPROVAL");
            update.put("claimedByUid", deviceAuthUid);
            update.put("claimedDeviceId", installId);

            Map<String, Object> devInfo = new HashMap<>();
            devInfo.put("deviceName", deviceName != null && !deviceName.isEmpty() ? deviceName : DeviceUtils.getDeviceName());
            devInfo.put("model", DeviceUtils.getDeviceModel());
            devInfo.put("androidVersion", DeviceUtils.getAndroidVersion());
            devInfo.put("batteryLevel", DeviceUtils.getBatteryPercentage(context));
            update.put("deviceInfo", devInfo);
            update.put("claimedAt", FieldValue.serverTimestamp());

            transaction.update(invRef, update);
            return snap.getString("ownerUid");
        }).addOnSuccessListener(ownerUid -> {
            // Listen for owner approval
            final ListenerRegistration[] reg = new ListenerRegistration[1];
            reg[0] = db.collection("pairing_invitations").document(tokenHash)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null) {
                            if (reg[0] != null) reg[0].remove();
                            callback.onError("Error waiting for approval: " + error.getMessage());
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {
                            String status = snapshot.getString("status");
                            if ("APPROVED".equals(status)) {
                                if (reg[0] != null) reg[0].remove();
                                sessionManager.setOwnerUid(ownerUid);
                                sessionManager.setCurrentDeviceId(installId);
                                sessionManager.setDeviceName(deviceName);

                                String bleSecret = snapshot.getString("bleSharedSecret");
                                if (bleSecret != null && !bleSecret.isEmpty()) {
                                    sessionManager.setBleSharedSecret(bleSecret);
                                }

                                Device enrolled = new Device(
                                        installId,
                                        deviceName,
                                        DeviceUtils.getDeviceModel(),
                                        DeviceUtils.getAndroidVersion(),
                                        DeviceUtils.getBatteryPercentage(context),
                                        "ONLINE",
                                        true,
                                        installId
                                );
                                callback.onSuccess(enrolled);
                            } else if ("REJECTED".equals(status)) {
                                if (reg[0] != null) reg[0].remove();
                                callback.onError("Device pairing was rejected by the owner.");
                            }
                        }
                    });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to submit pairing claim", e);
            callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to claim pairing token");
        });
    }

    /**
     * Spark-Compatible Revocation: Sets status = 'REVOKED' directly in Firestore.
     * Enforced immediately by Firestore Security Rules.
     */
    public void revokePairedDevice(String deviceId, ApiCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) {
            if (callback != null) callback.onError("No authenticated owner session");
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", "REVOKED");
        update.put("protectionEnabled", false);
        update.put("trackingStatus", "STOPPED");
        update.put("revokedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).collection("devices").document(deviceId)
                .update(update)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Revoke device failed in Firestore", e);
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
