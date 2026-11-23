package com.example.securetrack.fcm;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.example.securetrack.security.CommandExecutionManager;
import com.example.securetrack.sync.DurableSyncManager;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/**
 * High-reliability Firebase Cloud Messaging Service for SecureTrack.
 * Wakes up the background process or dormant application upon receiving a remote command notification.
 *
 * Security & Reliability Principles:
 * 1. FCM is strictly used as an immediate wake-up signal; actual commands are always fetched
 *    from Firestore and independently verified.
 * 2. Idempotent: duplicates are discarded.
 * 3. Never trust raw FCM data payload as authorization.
 * 4. Verifies targetDeviceId against the local installation identity.
 * 5. Uses WorkManager for reliable background execution.
 * 6. Never logs sensitive secrets, tokens or complete identifiers.
 */
public class SecureFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "SecureFcmService";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.i(TAG, "FCM registration token refreshed: " + DeviceUtils.redactIdentifier(token));

        Context context = getApplicationContext();
        SessionManager sessionManager = new SessionManager(context);
        sessionManager.setFcmToken(token);

        FcmTokenManager.syncFcmTokenToCloud(context, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (data == null || data.isEmpty()) {
            Log.d(TAG, "Received FCM message without data payload, ignoring.");
            return;
        }

        String commandId = data.get("commandId");
        String targetDeviceId = data.get("targetDeviceId");
        String ownerUid = data.get("ownerUid");
        String commandType = data.get("commandType");
        if (commandType == null || commandType.trim().isEmpty()) {
            commandType = data.get("type");
        }

        Log.i(TAG, "Received FCM command notification: type=" + commandType
                + ", cmdId=" + DeviceUtils.redactIdentifier(commandId)
                + ", target=" + DeviceUtils.redactIdentifier(targetDeviceId));

        Context context = getApplicationContext();
        SessionManager sessionManager = new SessionManager(context);
        String localInstallationId = sessionManager.getInstallationId();

        // 1. Target device verification
        if (targetDeviceId != null && !targetDeviceId.trim().isEmpty()) {
            if (!targetDeviceId.equals(localInstallationId)) {
                Log.w(TAG, "Ignoring FCM command intended for different target: " + DeviceUtils.redactIdentifier(targetDeviceId));
                return;
            }
        }

        // 2. Trigger immediate command sync & execution
        try {
            CommandExecutionManager.getInstance(context).pollAndExecuteCommands();
        } catch (Exception e) {
            Log.e(TAG, "Error invoking pollAndExecuteCommands from FCM", e);
        }

        // 3. Enqueue guaranteed background retry via WorkManager in case process is stopped
        try {
            DurableSyncManager.triggerImmediateSync(context);
        } catch (Exception e) {
            Log.w(TAG, "Error scheduling DurableSyncWorker from FCM: " + e.getMessage());
        }
    }
}
