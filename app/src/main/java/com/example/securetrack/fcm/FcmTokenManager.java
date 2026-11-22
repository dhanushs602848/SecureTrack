package com.example.securetrack.fcm;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Firebase Cloud Messaging registration token lifecycle:
 * - Token retrieval and refresh.
 * - Secure association with the authenticated device document.
 * - Storing in SessionManager.
 * - Secure invalidation on logout / unpair.
 * - Never logs or exposes complete FCM registration tokens in public logs.
 */
public class FcmTokenManager {

    private static final String TAG = "FcmTokenManager";

    /**
     * Retrieves current FCM registration token and synchronizes with cloud if registered.
     */
    public static void registerOrRefreshFcmToken(@NonNull Context context) {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null) {
                    Log.w(TAG, "Fetching FCM registration token failed: " + (task.getException() != null ? task.getException().getMessage() : "unknown"));
                    return;
                }

                String token = task.getResult();
                Log.i(TAG, "Acquired FCM registration token: " + DeviceUtils.redactIdentifier(token));

                SessionManager sessionManager = new SessionManager(context);
                sessionManager.setFcmToken(token);

                syncFcmTokenToCloud(context, token);
            });
        } catch (Exception e) {
            Log.w(TAG, "FCM is unavailable or Google Play Services missing: " + e.getMessage());
        }
    }

    /**
     * Synchronizes FCM token with the device document under users/{ownerUid}/devices/{deviceId}.
     */
    public static void syncFcmTokenToCloud(@NonNull Context context, @NonNull String token) {
        SessionManager sessionManager = new SessionManager(context);
        String ownerUid = sessionManager.getOwnerUid();
        String deviceId = sessionManager.getInstallationId();

        if (ownerUid == null || ownerUid.trim().isEmpty() || deviceId == null || deviceId.trim().isEmpty()) {
            Log.d(TAG, "Cannot sync FCM token to cloud yet: device is not enrolled under an ownerUid.");
            return;
        }

        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("fcmToken", token);
            updates.put("fcmTokenUpdatedAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("users").document(ownerUid)
                    .collection("devices").document(deviceId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "FCM registration token safely associated with device " + DeviceUtils.redactIdentifier(deviceId)))
                    .addOnFailureListener(e -> Log.w(TAG, "Failed to persist FCM token on device document: " + e.getMessage()));
        } catch (Exception e) {
            Log.w(TAG, "Error initiating FCM token cloud update: " + e.getMessage());
        }
    }

    /**
     * Invalidates and deletes the current FCM token (e.g. on logout or unpair).
     */
    public static void invalidateFcmToken(@NonNull Context context) {
        SessionManager sessionManager = new SessionManager(context);
        String ownerUid = sessionManager.getOwnerUid();
        String deviceId = sessionManager.getInstallationId();

        sessionManager.setFcmToken(null);

        if (ownerUid != null && deviceId != null) {
            try {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fcmToken", FieldValue.delete());
                updates.put("fcmTokenInvalidatedAt", FieldValue.serverTimestamp());

                FirebaseFirestore.getInstance()
                        .collection("users").document(ownerUid)
                        .collection("devices").document(deviceId)
                        .update(updates)
                        .addOnSuccessListener(aVoid -> Log.i(TAG, "Cleared FCM token on device document"))
                        .addOnFailureListener(e -> Log.d(TAG, "Could not delete FCM token in Firestore: " + e.getMessage()));
            } catch (Exception ignored) {}
        }

        try {
            FirebaseMessaging.getInstance().deleteToken()
                    .addOnSuccessListener(aVoid -> Log.i(TAG, "FCM instance token deleted successfully"))
                    .addOnFailureListener(e -> Log.d(TAG, "FCM deleteToken error: " + e.getMessage()));
        } catch (Exception ignored) {}
    }
}
