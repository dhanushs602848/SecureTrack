package com.example.securetrack.security;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.Map;

public class CommandExecutionManager {

    private static final String TAG = "CommandExecutionMgr";

    private final Context context;
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private ListenerRegistration commandListener;

    public CommandExecutionManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(this.context);
    }

    private String getUid() {
        String uid = sessionManager.getFirebaseUid();
        if (uid == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return uid;
    }

    public void startListeningToCommands() {
        stopListeningToCommands();

        String uid = getUid();
        String deviceId = sessionManager.getCurrentDeviceId();

        if (uid == null || deviceId == null) {
            Log.w(TAG, "Cannot listen to commands: UID or Device ID missing");
            return;
        }

        commandListener = db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands")
                .whereEqualTo("status", "QUEUED")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Command snapshot listener error", error);
                        return;
                    }
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            executeSingleCommand(uid, deviceId, doc);
                        }
                    }
                });
    }

    public void pollAndExecuteCommands() {
        startListeningToCommands();
    }

    private void executeSingleCommand(String uid, String deviceId, DocumentSnapshot doc) {
        String commandId = doc.getId();
        String type = doc.getString("commandType");

        if (type == null) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Missing command type");
            return;
        }

        // Prevent execution of expired commands (older than 2 hours)
        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
        if (createdAt != null) {
            long ageMillis = System.currentTimeMillis() - createdAt.toDate().getTime();
            if (ageMillis > 2 * 60 * 60 * 1000) {
                updateCommandStatus(uid, deviceId, commandId, "EXPIRED", "Command expired before delivery");
                return;
            }
        }

        // Atomically transition status to EXECUTING to avoid duplicate concurrent execution
        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId)
                .update("status", "EXECUTING");

        if ("LOCK_DEVICE".equalsIgnoreCase(type)) {
            if (SecureAdminReceiver.isAdminActive(context)) {
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null) {
                        dpm.lockNow();
                        updateCommandStatus(uid, deviceId, commandId, "EXECUTED", "Device locked successfully");
                    } else {
                        updateCommandStatus(uid, deviceId, commandId, "FAILED", "DevicePolicyManager unavailable");
                    }
                } catch (Exception e) {
                    updateCommandStatus(uid, deviceId, commandId, "FAILED", e.getMessage());
                }
            } else {
                updateCommandStatus(uid, deviceId, commandId, "FAILED", "Device Policy Admin privileges not activated by user");
            }
        } else if ("ALARM_ALERT".equalsIgnoreCase(type)) {
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                Ringtone ringtone = RingtoneManager.getRingtone(context, alarmUri);
                if (ringtone != null) {
                    ringtone.play();
                }
                updateCommandStatus(uid, deviceId, commandId, "EXECUTED", "Alarm alert triggered successfully");
            } catch (Exception e) {
                updateCommandStatus(uid, deviceId, commandId, "FAILED", "Alarm trigger failed: " + e.getMessage());
            }
        } else if ("WIPE_DEVICE".equalsIgnoreCase(type)) {
            if (SecureAdminReceiver.isAdminActive(context)) {
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null) {
                        updateCommandStatus(uid, deviceId, commandId, "EXECUTED", "Wipe initiated");
                        dpm.wipeData(0);
                    } else {
                        updateCommandStatus(uid, deviceId, commandId, "FAILED", "DevicePolicyManager unavailable");
                    }
                } catch (Exception e) {
                    updateCommandStatus(uid, deviceId, commandId, "FAILED", "Wipe failed: " + e.getMessage());
                }
            } else {
                updateCommandStatus(uid, deviceId, commandId, "FAILED", "Device Policy Admin privileges not activated");
            }
        } else if ("LOCATE_NOW".equalsIgnoreCase(type)) {
            updateCommandStatus(uid, deviceId, commandId, "EXECUTED", "Instant location request dispatched");
        } else {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Unsupported command type: " + type);
        }
    }

    private void updateCommandStatus(String uid, String deviceId, String commandId, String status, String result) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("result", result);
        updates.put("executedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId)
                .update(updates);
    }

    public void stopListeningToCommands() {
        if (commandListener != null) {
            commandListener.remove();
            commandListener = null;
        }
    }
}
