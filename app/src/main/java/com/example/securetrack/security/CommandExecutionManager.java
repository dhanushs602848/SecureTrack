package com.example.securetrack.security;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.location.Location;
import android.util.Log;
import com.example.securetrack.ble.BleNearbyManager;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class CommandExecutionManager {

    private static final String TAG = "CommandExecutionMgr";

    private static final long EXPIRY_LOCK_ALARM_MS = 15 * 60 * 1000L; // 15 mins
    private static final long EXPIRY_LOCATE_MS = 30 * 60 * 1000L;     // 30 mins

    private static volatile CommandExecutionManager instance;

    private final Context context;
    private final FirebaseFirestore db;
    private final SessionManager sessionManager;
    private final LocationRepository locationRepository;
    private com.example.securetrack.location.ISecureLocationProvider locationProvider;
    private final AppDatabase appDatabase;
    private final Set<String> processedCommandIds = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private ListenerRegistration commandListener;

    public static CommandExecutionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CommandExecutionManager.class) {
                if (instance == null) {
                    instance = new CommandExecutionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public CommandExecutionManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(this.context);
        this.locationRepository = new LocationRepository(this.context);
        this.locationProvider = new com.example.securetrack.location.FusedLocationProviderImpl(this.context);
        this.appDatabase = AppDatabase.getInstance(this.context);
    }

    public void setLocationProvider(com.example.securetrack.location.ISecureLocationProvider provider) {
        this.locationProvider = provider;
    }

    private String getUid() {
        if (sessionManager.isPairedSecondaryDevice()) {
            String pairedOwnerUid = sessionManager.getPairedOwnerUid();
            if (pairedOwnerUid != null && !pairedOwnerUid.isEmpty()) {
                return pairedOwnerUid;
            }
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            if (auth.getCurrentUser().isAnonymous()) {
                String ownerUid = sessionManager.getOwnerUid();
                if (ownerUid != null && !ownerUid.isEmpty()) {
                    return ownerUid;
                }
            } else {
                if (!sessionManager.isPairedSecondaryDevice()) {
                    sessionManager.setOwnerUid(auth.getCurrentUser().getUid());
                    return auth.getCurrentUser().getUid();
                }
            }
        }
        return sessionManager.getOwnerUid();
    }

    private String getDeviceId() {
        String deviceId = sessionManager.getCurrentDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = sessionManager.getInstallationId();
        }
        return deviceId;
    }

    public synchronized void startListeningToCommands() {
        stopListeningToCommands();

        // Self-heal pairing configuration if this device was enrolled as a secondary device
        // but local SharedPreferences was reset or missing the secondary paired flag
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null && !sessionManager.isPairedSecondaryDevice()) {
            String currentAuthUid = auth.getCurrentUser().getUid();
            db.collection("pairing_invitations")
                    .whereEqualTo("claimedByUid", currentAuthUid)
                    .whereEqualTo("status", "APPROVED")
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                            String ownerUid = doc.getString("ownerUid");
                            String claimedDevId = doc.getString("claimedDeviceId");
                            if (ownerUid != null && !ownerUid.isEmpty() && claimedDevId != null && !claimedDevId.isEmpty()) {
                                Log.i(TAG, "Self-healing pairing state: Enrolled secondary device under owner " + ownerUid);
                                sessionManager.setPairedSecondaryDevice(true);
                                sessionManager.setPairedOwnerUid(ownerUid);
                                sessionManager.setOwnerUid(ownerUid);
                                sessionManager.setCurrentDeviceId(claimedDevId);
                                startListeningToCommandsInternal();
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.d(TAG, "Pairing self-heal check completed: " + e.getMessage()));
        }

        startListeningToCommandsInternal();
    }

    private synchronized void startListeningToCommandsInternal() {
        if (commandListener != null) {
            commandListener.remove();
            commandListener = null;
        }

        String uid = getUid();
        String deviceId = getDeviceId();

        if (uid == null || deviceId == null) {
            Log.w(TAG, "Cannot listen to commands: UID or Device ID missing (uid=" + uid + ", devId=" + deviceId + ")");
            return;
        }

        Log.i(TAG, "Listening for remote commands on users/" + uid + "/devices/" + deviceId + "/commands");

        // Start BLE peripheral advertising so nearby controller can find this device if offline
        BleNearbyManager.getInstance(context).startAdvertisingAsTarget();

        // 1. One-time fetch for pending QUEUED commands that arrived while process was offline
        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands")
                .whereEqualTo("status", "QUEUED")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            executeSingleCommand(uid, deviceId, doc);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching QUEUED commands on startup", e));

        // 2. Real-time snapshot listener for incoming commands via Firebase Cloud
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

        Log.i(TAG, "Executing remote command " + commandId + " of type " + type + " on target device " + deviceId);

        // Atomically transition status to EXECUTING in Firestore
        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId)
                .update("status", "EXECUTING");

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

        locationProvider.getCurrentLocation(new com.example.securetrack.location.ISecureLocationProvider.LocationCallback() {
            @Override
            public void onSuccess(Location location) {
                recordAndCompleteLocate(uid, deviceId, commandId, finalRequestId, location);
            }

            @Override
            public void onFailure(Exception exception) {
                updateCommandStatus(uid, deviceId, commandId, "FAILED", "Location retrieval failed: " + (exception != null ? exception.getMessage() : "Unknown error"));
            }
        });
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

        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord result) {
                Map<String, Object> devUpdates = new HashMap<>();
                devUpdates.put("lastSeen", FieldValue.serverTimestamp());
                devUpdates.put("batteryLevel", DeviceUtils.getBatteryLevel(context));
                devUpdates.put("locationStatus", "ACTIVE");
                devUpdates.put("status", "ONLINE");
                devUpdates.put("lastLatitude", location.getLatitude());
                devUpdates.put("lastLongitude", location.getLongitude());
                devUpdates.put("lastAccuracy", location.getAccuracy());
                devUpdates.put("locationAccuracy", location.getAccuracy());
                devUpdates.put("lastRequestId", requestId);

                db.collection("users").document(uid)
                        .collection("devices").document(deviceId)
                        .set(devUpdates, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> Log.i(TAG, "Target device location telemetry updated in Firestore"))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update target device location telemetry", e));

                String resultMsg = String.format(Locale.US, "Location: (%.5f, %.5f) ±%.1fm [req:%s]",
                        location.getLatitude(), location.getLongitude(), location.getAccuracy(), requestId);
                updateCommandStatusWithRequestId(uid, deviceId, commandId, "EXECUTED", resultMsg, requestId);
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Subcollection location write failed: " + errorMessage + "; updating device document directly");
                Map<String, Object> devUpdates = new HashMap<>();
                devUpdates.put("lastSeen", FieldValue.serverTimestamp());
                devUpdates.put("batteryLevel", DeviceUtils.getBatteryLevel(context));
                devUpdates.put("locationStatus", "ACTIVE");
                devUpdates.put("status", "ONLINE");
                devUpdates.put("lastLatitude", location.getLatitude());
                devUpdates.put("lastLongitude", location.getLongitude());
                devUpdates.put("lastAccuracy", location.getAccuracy());
                devUpdates.put("locationAccuracy", location.getAccuracy());
                devUpdates.put("lastRequestId", requestId);

                db.collection("users").document(uid)
                        .collection("devices").document(deviceId)
                        .set(devUpdates, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> Log.i(TAG, "Target device location telemetry updated directly in Firestore"))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update direct location telemetry", e));

                String resultMsg = String.format(Locale.US, "Location: (%.5f, %.5f) ±%.1fm (Telemetry synced) [req:%s]",
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
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.i(TAG, "Command " + commandId + " updated to " + status + " (" + result + ")"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update command " + commandId + " to " + status, e));

        new Thread(() -> {
            appDatabase.commandDao().updateExecutionStatus(commandId, status, result, System.currentTimeMillis());
        }).start();
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

        String deviceName = sessionManager.getDeviceName();
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = android.os.Build.MODEL != null ? android.os.Build.MODEL : "Android Device";
        }
        final String finalDeviceName = deviceName;
        final String alertId = "alarm_" + (commandId != null ? commandId : java.util.UUID.randomUUID().toString());

        updateCommandStatus(uid, deviceId, commandId, "EXECUTING", "Activating 5-second panic security siren");

        alarmManager.playFiveSecondAlarm(new AlarmSoundManager.AlarmCallback() {
            @Override
            public void onAlarmStarted() {
                Log.i(TAG, "5-second panic security siren started on " + finalDeviceName);

                Map<String, Object> alertMap = new HashMap<>();
                alertMap.put("id", alertId);
                alertMap.put("deviceId", deviceId);
                alertMap.put("deviceName", finalDeviceName);
                alertMap.put("eventType", "SECURITY_ALARM_TRIGGERED");
                alertMap.put("severity", "CRITICAL");
                alertMap.put("description", "Panic security siren activated on " + finalDeviceName);
                alertMap.put("status", "ACTIVE");
                alertMap.put("commandId", commandId);
                alertMap.put("triggerSource", "REMOTE_COMMAND");
                alertMap.put("executionStatus", "STARTED");
                alertMap.put("timestamp", FieldValue.serverTimestamp());

                db.collection("users").document(uid)
                        .collection("alerts").document(alertId)
                        .set(alertMap, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> Log.i(TAG, "Security alert recorded: " + alertId))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to record security alert in Firestore", e));

                new Thread(() -> {
                    com.example.securetrack.database.SecurityAlertEntity entity = new com.example.securetrack.database.SecurityAlertEntity(
                            alertId,
                            deviceId,
                            finalDeviceName,
                            "SECURITY_ALARM_TRIGGERED",
                            "CRITICAL",
                            "Panic security siren activated on " + finalDeviceName,
                            "ENABLED",
                            "ACTIVE",
                            System.currentTimeMillis(),
                            0, null, 0, true
                    );
                    appDatabase.securityAlertDao().insertOrUpdate(entity);
                }).start();

                showAlarmNotification(finalDeviceName, "STARTED", alertId);
            }

            @Override
            public void onAlarmCompleted(String reason) {
                updateCommandStatus(uid, deviceId, commandId, "EXECUTED", reason);

                Map<String, Object> update = new HashMap<>();
                update.put("executionStatus", "COMPLETED");
                update.put("completedAt", FieldValue.serverTimestamp());

                db.collection("users").document(uid)
                        .collection("alerts").document(alertId)
                        .update(update)
                        .addOnSuccessListener(aVoid -> Log.i(TAG, "Security alert completed: " + alertId))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update alert completion", e));

                showAlarmNotification(finalDeviceName, "COMPLETED", alertId);
            }
        });
    }

    private void showAlarmNotification(String deviceName, String executionStatus, String alertId) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            String channelId = "securetrack_panic_siren";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId,
                        "Panic Security Siren",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Emergency alerts for panic security siren activations");
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }

            android.content.Intent intent = new android.content.Intent(context, com.example.securetrack.MainActivity.class);
            intent.putExtra("navigate_to", "security");
            intent.putExtra("alert_id", alertId);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(context, 101, intent, flags);

            String statusText = "STARTED".equals(executionStatus)
                    ? "Panic siren activated and sounding for 5 seconds."
                    : "Panic siren cycle completed. Alert recorded in Security Center.";

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("SecureTrack Security Alarm: " + deviceName)
                    .setContentText(statusText)
                    .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(
                            statusText + "\nDevice: " + deviceName + "\nStatus: " + executionStatus + "\nTap to review and dismiss in Security Center."
                    ))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            nm.notify(9001, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Error displaying alarm notification", e);
        }
    }

    private void handleWipeCommand(String uid, String deviceId, String commandId) {
        Log.w(TAG, "Rejecting WIPE_DEVICE command: Safety lock enforced.");
        updateCommandStatus(uid, deviceId, commandId, "FAILED",
                "Safety Lock: Factory wipe is permanently disabled in this release to protect user data.");
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
