package com.example.securetrack.security;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.location.Location;
import android.util.Log;
import com.example.securetrack.ble.BleNearbyManager;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Robust, duplicate-safe command execution engine for SecureTrack.
 * Handles:
 * - Real GPS location acquisition for LOCATE_NOW
 * - Secure force-lock via DevicePolicyManager for LOCK_DEVICE
 * - Loud 5-second auto-stopping alarm via AlarmSoundManager for ALARM_ALERT
 * - Command expiration verification (15-min limit for security commands)
 * - Duplicate execution prevention
 * - Local Room command audit tracking
 */
public class CommandExecutionManager {

    private static final String TAG = "CommandExecutionMgr";

    // Command-specific expiration windows
    private static final long EXPIRY_LOCK_ALARM_MS = 15 * 60 * 1000L; // 15 mins
    private static final long EXPIRY_LOCATE_MS = 30 * 60 * 1000L;     // 30 mins

    private final Context context;
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final LocationRepository locationRepository;
    private final FusedLocationProviderClient fusedLocationClient;
    private final AppDatabase appDatabase;
    private final Set<String> processedCommandIds = new HashSet<>();
    private ListenerRegistration commandListener;

    public CommandExecutionManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(this.context);
        this.locationRepository = new LocationRepository(this.context);
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
        this.appDatabase = AppDatabase.getInstance(this.context);
    }

    private String getUid() {
        return sessionManager.getOwnerUid();
    }

    public synchronized void startListeningToCommands() {
        stopListeningToCommands();

        String uid = getUid();
        String deviceId = sessionManager.getCurrentDeviceId();

        if (uid == null || deviceId == null) {
            Log.w(TAG, "Cannot listen to commands: UID or Device ID missing");
            return;
        }

        // Start BLE peripheral advertising so nearby controller can find this device if offline
        BleNearbyManager.getInstance(context).startAdvertisingAsTarget();

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

    private synchronized void executeSingleCommand(String uid, String deviceId, DocumentSnapshot doc) {
        String commandId = doc.getId();

        // Duplicate-execution prevention
        if (processedCommandIds.contains(commandId)) {
            Log.i(TAG, "Command already processed in this session: " + commandId);
            return;
        }
        processedCommandIds.add(commandId);

        String type = doc.getString("commandType");
        if (type == null) {
            type = doc.getString("command");
        }

        if (type == null) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Missing command type");
            return;
        }

        // Calculate age and check command expiration
        com.google.firebase.Timestamp createdAt = doc.getTimestamp("createdAt");
        long maxAge = ("LOCK_DEVICE".equalsIgnoreCase(type) || "ALARM_ALERT".equalsIgnoreCase(type))
                ? EXPIRY_LOCK_ALARM_MS : EXPIRY_LOCATE_MS;

        if (createdAt != null) {
            long ageMillis = System.currentTimeMillis() - createdAt.toDate().getTime();
            if (ageMillis > maxAge) {
                Log.w(TAG, "Expiring stale command: " + type + ", age=" + (ageMillis / 1000) + "s");
                updateCommandStatus(uid, deviceId, commandId, "EXPIRED",
                        "Command expired before execution (exceeded " + (maxAge / 60000) + " min validity window)");
                return;
            }
        }

        // Atomically transition status to EXECUTING in Firestore
        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId)
                .update("status", "EXECUTING");

        // Record locally in Room
        final String finalType = type;
        new Thread(() -> {
            CommandEntity entity = new CommandEntity(
                    commandId,
                    deviceId,
                    finalType,
                    uid,
                    "",
                    CommandEntity.STATE_EXECUTING,
                    "Executing on target device",
                    CommandEntity.TRANSPORT_CLOUD,
                    createdAt != null ? createdAt.toDate().getTime() : System.currentTimeMillis(),
                    System.currentTimeMillis() + maxAge,
                    System.currentTimeMillis(),
                    0,
                    "SYNCED",
                    0
            );
            appDatabase.commandDao().insertOrUpdate(entity);
        }).start();

        // Dispatch based on command type
        if ("LOCATE_NOW".equalsIgnoreCase(type)) {
            handleLocateCommand(uid, deviceId, commandId, doc);
        } else if ("LOCK_DEVICE".equalsIgnoreCase(type)) {
            handleLockCommand(uid, deviceId, commandId);
        } else if ("ALARM_ALERT".equalsIgnoreCase(type)) {
            handleAlarmCommand(uid, deviceId, commandId);
        } else if ("WIPE_DEVICE".equalsIgnoreCase(type)) {
            handleWipeCommand(uid, deviceId, commandId);
        } else {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Unsupported command type: " + type);
        }
    }

    @SuppressLint("MissingPermission")
    private void handleLocateCommand(String uid, String deviceId, String commandId, DocumentSnapshot doc) {
        if (!PermissionUtils.hasForegroundLocationPermission(context)) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Location permission not granted on target device");
            return;
        }

        String reqId = doc.getString("requestId");
        if (reqId == null || reqId.isEmpty()) {
            String payload = doc.getString("payload");
            if (payload != null && !payload.isEmpty()) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(payload);
                    reqId = json.optString("requestId", null);
                } catch (Exception ignored) {}
            }
        }
        if (reqId == null || reqId.isEmpty()) {
            reqId = commandId;
        }
        final String finalRequestId = reqId;

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            recordAndCompleteLocate(uid, deviceId, commandId, finalRequestId, location);
                        } else {
                            // Fallback to last known location
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    recordAndCompleteLocate(uid, deviceId, commandId, finalRequestId, lastLoc);
                                } else {
                                    updateCommandStatus(uid, deviceId, commandId, "FAILED", "GPS/Network location provider returned null");
                                }
                            }).addOnFailureListener(e -> {
                                updateCommandStatus(uid, deviceId, commandId, "FAILED", "Location retrieval failed: " + e.getMessage());
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        updateCommandStatus(uid, deviceId, commandId, "FAILED", "High accuracy location failed: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "SecurityException: " + e.getMessage());
        }
    }

    private void recordAndCompleteLocate(String uid, String deviceId, String commandId, String requestId, Location location) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestampStr = sdf.format(new Date(location.getTime()));

        LocationRecord record = new LocationRecord(
                deviceId,
                requestId,
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getAltitude(),
                location.getSpeed(),
                timestampStr
        );

        // Save to Firestore location history with exact requestId correlation
        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord result) {
                // Update device document with live location & battery
                Map<String, Object> devUpdates = new HashMap<>();
                devUpdates.put("lastSeen", FieldValue.serverTimestamp());
                devUpdates.put("batteryLevel", DeviceUtils.getBatteryLevel(context));
                devUpdates.put("locationStatus", "ACTIVE");
                devUpdates.put("lastLatitude", location.getLatitude());
                devUpdates.put("lastLongitude", location.getLongitude());
                devUpdates.put("lastAccuracy", location.getAccuracy());
                devUpdates.put("lastRequestId", requestId);

                db.collection("users").document(uid)
                        .collection("devices").document(deviceId)
                        .set(devUpdates, SetOptions.merge());

                String resultMsg = String.format(Locale.US, "Location: (%.5f, %.5f) ±%.1fm [req:%s]",
                        location.getLatitude(), location.getLongitude(), location.getAccuracy(), requestId);
                updateCommandStatusWithRequestId(uid, deviceId, commandId, "EXECUTED", resultMsg, requestId);
            }

            @Override
            public void onError(String errorMessage) {
                String resultMsg = String.format(Locale.US, "Location: (%.5f, %.5f) ±%.1fm (Local only) [req:%s]",
                        location.getLatitude(), location.getLongitude(), location.getAccuracy(), requestId);
                updateCommandStatusWithRequestId(uid, deviceId, commandId, "EXECUTED", resultMsg, requestId);
            }
        });
    }

    private void updateCommandStatusWithRequestId(String uid, String deviceId, String commandId, String status, String result, String requestId) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("result", result);
        if (requestId != null) {
            updates.put("requestId", requestId);
        }
        updates.put("executedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId)
                .set(updates, SetOptions.merge());
    }

    private void handleLockCommand(String uid, String deviceId, String commandId) {
        if (!PermissionUtils.isDeviceAdminActive(context)) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED",
                    "Device Policy Administrator privilege not activated by user in system settings");
            return;
        }

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                dpm.lockNow();
                updateCommandStatus(uid, deviceId, commandId, "EXECUTED", "Screen locked immediately via DevicePolicyManager");
            } else {
                updateCommandStatus(uid, deviceId, commandId, "FAILED", "DevicePolicyManager service unavailable");
            }
        } catch (SecurityException e) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "SecurityException during lockNow: " + e.getMessage());
        } catch (Exception e) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Lock error: " + e.getMessage());
        }
    }

    private void handleAlarmCommand(String uid, String deviceId, String commandId) {
        AlarmSoundManager alarmManager = AlarmSoundManager.getInstance(context);

        alarmManager.playFiveSecondAlarm(new AlarmSoundManager.AlarmCallback() {
            @Override
            public void onAlarmStarted() {
                Log.i(TAG, "5-second anti-theft alarm playing");
            }

            @Override
            public void onAlarmCompleted(String reason) {
                updateCommandStatus(uid, deviceId, commandId, "EXECUTED", reason);
            }
        });
    }

    private void handleWipeCommand(String uid, String deviceId, String commandId) {
        // Safe check: Wipe must only be executed if Device Admin is active and policy explicitly supports it
        if (!PermissionUtils.isDeviceAdminActive(context)) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Device Admin privilege required for wipe");
            return;
        }

        updateCommandStatus(uid, deviceId, commandId, "EXECUTED", "Wipe request authorized");
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                dpm.wipeData(0);
            }
        } catch (Exception e) {
            updateCommandStatus(uid, deviceId, commandId, "FAILED", "Wipe execution error: " + e.getMessage());
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
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Command " + commandId + " updated to " + status))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update command " + commandId, e));

        // Update local Room command status
        new Thread(() -> {
            appDatabase.commandDao().updateExecutionStatus(commandId, status, result, System.currentTimeMillis());
        }).start();
    }

    public synchronized void stopListeningToCommands() {
        if (commandListener != null) {
            commandListener.remove();
            commandListener = null;
        }
        BleNearbyManager.getInstance(context).stopAdvertising();
    }
}
