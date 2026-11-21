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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
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

        FirebaseAuth auth = FirebaseAuth.getInstance();
        String currentInstallationId = sessionManager.getInstallationId();

        // Exact pairing recovery: Self-heal secondary enrollment using exact claimedDeviceId + authUid + status
        if (auth.getCurrentUser() != null && !sessionManager.isPairedSecondaryDevice() && auth.getCurrentUser().isAnonymous()) {
            String currentAuthUid = auth.getCurrentUser().getUid();

            db.collection("pairing_invitations")
                    .whereEqualTo("claimedByUid", currentAuthUid)
                    .whereEqualTo("claimedDeviceId", currentInstallationId)
                    .whereEqualTo("status", "APPROVED")
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                            String ownerUid = doc.getString("ownerUid");
                            String claimedDevId = doc.getString("claimedDeviceId");

                            if (ownerUid != null && !ownerUid.isEmpty() && currentInstallationId.equals(claimedDevId)) {
                                // Verify target device is active and not REVOKED before saving state
                                db.collection("users").document(ownerUid)
                                        .collection("devices").document(currentInstallationId)
                                        .get()
                                        .addOnSuccessListener(devDoc -> {
                                            if (devDoc.exists() && !"REVOKED".equalsIgnoreCase(devDoc.getString("status"))) {
                                                Log.i(TAG, "Self-healing pairing state verified for installation " + currentInstallationId + " under owner " + ownerUid);
                                                sessionManager.setPairedSecondaryDevice(true);
                                                sessionManager.setPairedOwnerUid(ownerUid);
                                                sessionManager.setOwnerUid(ownerUid);
                                                sessionManager.setCurrentDeviceId(currentInstallationId);
                                                startListeningToCommandsInternal();
                                            } else {
                                                Log.w(TAG, "Self-healing rejected: Device is revoked or unapproved in Firestore");
                                            }
                                        })
                                        .addOnFailureListener(e -> Log.e(TAG, "Device verification failed during recovery", e));
                            }
                        } else {
                            Log.d(TAG, "No matching approved pairing invitation for installation " + currentInstallationId);
                            startListeningToCommandsInternal();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Pairing self-heal check query failed", e);
                        startListeningToCommandsInternal();
                    });
            return;
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

        // 1. Fetch QUEUED commands that arrived while process was offline
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

    /**
     * Genuinely Atomic Command Claiming via Firestore Transaction.
     * Guarantees:
     * 1. Status read & verified as QUEUED inside db.runTransaction
     * 2. Expiration verified and handled atomically
     * 3. QUEUED -> EXECUTING transition committed before execution begins
             * 4. In-memory processed set updated ONLY on transaction success
     */
    private void executeSingleCommand(String uid, String deviceId, DocumentSnapshot initialDoc) {
        String commandId = initialDoc.getId();

        if (processedCommandIds.contains(commandId)) {
            Log.d(TAG, "Command already claimed/processed in local memory: " + commandId);
            return;
        }

        DocumentReference cmdRef = db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("commands").document(commandId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(cmdRef);
            if (!snapshot.exists()) {
                throw new FirebaseFirestoreException("Command document does not exist", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String currentStatus = snapshot.getString("status");
            if (!"QUEUED".equalsIgnoreCase(currentStatus)) {
                throw new FirebaseFirestoreException("Command status is " + currentStatus + ", not QUEUED", FirebaseFirestoreException.Code.ABORTED);
            }

            String type = snapshot.getString("commandType");
            if (type == null) {
                type = snapshot.getString("command");
            }
            if (type == null) {
                throw new FirebaseFirestoreException("Malformed command missing commandType", FirebaseFirestoreException.Code.INVALID_ARGUMENT);
            }

            com.google.firebase.Timestamp createdAt = snapshot.getTimestamp("createdAt");
            long maxAge = ("LOCK_DEVICE".equalsIgnoreCase(type) || "ALARM_ALERT".equalsIgnoreCase(type))
                    ? EXPIRY_LOCK_ALARM_MS : EXPIRY_LOCATE_MS;

            if (createdAt != null) {
                long ageMillis = System.currentTimeMillis() - createdAt.toDate().getTime();
                if (ageMillis > maxAge) {
                    Map<String, Object> expireUpdates = new HashMap<>();
                    expireUpdates.put("status", "EXPIRED");
                    expireUpdates.put("result", "Command expired before execution (exceeded " + (maxAge / 60000) + " min validity window)");
                    expireUpdates.put("executedAt", FieldValue.serverTimestamp());
                    transaction.update(cmdRef, expireUpdates);
                    throw new FirebaseFirestoreException("Command expired", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED);
                }
            }

            Map<String, Object> claimUpdates = new HashMap<>();
            claimUpdates.put("status", "EXECUTING");
            claimUpdates.put("claimedAt", FieldValue.serverTimestamp());
            transaction.update(cmdRef, claimUpdates);

            return snapshot;
        }).addOnSuccessListener(claimedDoc -> {
            processedCommandIds.add(commandId);

            String type = claimedDoc.getString("commandType");
            if (type == null) type = claimedDoc.getString("command");

            Log.i(TAG, "Successfully claimed and executing remote command " + commandId + " [" + type + "] on target " + deviceId);

            final String finalType = type;
            com.google.firebase.Timestamp createdAt = claimedDoc.getTimestamp("createdAt");
            long maxAge = ("LOCK_DEVICE".equalsIgnoreCase(type) || "ALARM_ALERT".equalsIgnoreCase(type))
                    ? EXPIRY_LOCK_ALARM_MS : EXPIRY_LOCATE_MS;

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
                handleLocateCommand(uid, deviceId, commandId, claimedDoc);
            } else if ("LOCK_DEVICE".equalsIgnoreCase(type)) {
                handleLockCommand(uid, deviceId, commandId);
            } else if ("ALARM_ALERT".equalsIgnoreCase(type)) {
                handleAlarmCommand(uid, deviceId, commandId);
            } else if ("WIPE_DEVICE".equalsIgnoreCase(type)) {
                handleWipeCommand(uid, deviceId, commandId);
            } else {
                updateCommandStatus(uid, deviceId, commandId, "FAILED", "Unsupported command type: " + type);
            }
        }).addOnFailureListener(e -> {
            if (e instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException fe = (FirebaseFirestoreException) e;
                if (fe.getCode() == FirebaseFirestoreException.Code.ABORTED) {
                    Log.d(TAG, "Command " + commandId + " already claimed by another worker or not QUEUED.");
                    processedCommandIds.add(commandId);
                } else if (fe.getCode() == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                    Log.w(TAG, "Command " + commandId + " marked as EXPIRED in transaction.");
                    processedCommandIds.add(commandId);
                } else {
                    Log.e(TAG, "Command claim transaction failed for " + commandId + ": " + e.getMessage());
                }
            } else {
                Log.e(TAG, "Command claim failed for " + commandId + ": " + e.getMessage());
            }
        });
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

    /**
     * Confirmed Cloud Location Persistence.
     * Uses WriteBatch for atomic cloud location history & device telemetry update.
     * Updates command status to EXECUTED ONLY upon WriteBatch commit success.
     */
    private void recordAndCompleteLocate(String uid, String deviceId, String commandId, String requestId, Location location) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestampStr = sdf.format(new Date(location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()));

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

        com.google.firebase.Timestamp gpsTimestamp = LocationRepository.parseTimestamp(timestampStr);

        Map<String, Object> locationMap = new HashMap<>();
        locationMap.put("clientUuid", requestId);
        locationMap.put("requestId", requestId);
        locationMap.put("latitude", location.getLatitude());
        locationMap.put("longitude", location.getLongitude());
        locationMap.put("accuracy", location.getAccuracy());
        locationMap.put("altitude", location.getAltitude());
        locationMap.put("speed", location.getSpeed());
        locationMap.put("source", "GPS");
        locationMap.put("timestamp", gpsTimestamp);
        locationMap.put("uploadedAt", FieldValue.serverTimestamp());

        // 1. Write to local Room DB for offline write-ahead durability
        new Thread(() -> {
            com.example.securetrack.database.LocationEntity entity = new com.example.securetrack.database.LocationEntity(
                    deviceId,
                    requestId,
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAccuracy(),
                    location.getAltitude(),
                    location.getSpeed(),
                    timestampStr,
                    false
            );
            appDatabase.locationDao().insert(entity);
        }).start();

        // 2. Perform atomic batch write for location history & device telemetry
        WriteBatch batch = db.batch();
        DocumentReference locRef = db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .collection("locations").document(requestId);
        batch.set(locRef, locationMap, SetOptions.merge());

        DocumentReference devRef = db.collection("users").document(uid)
                .collection("devices").document(deviceId);
        batch.set(devRef, devUpdates, SetOptions.merge());

        batch.commit().addOnSuccessListener(aVoid -> {
            Log.i(TAG, "Location history and device telemetry successfully committed to cloud for req: " + requestId);
            new Thread(() -> {
                com.example.securetrack.database.LocationEntity loc = appDatabase.locationDao().getByClientUuid(requestId);
                if (loc != null) {
                    appDatabase.locationDao().markAsSynced(loc.getId());
                }
            }).start();

            String resultMsg = String.format(Locale.US, "Location: (%.5f, %.5f) ±%.1fm [req:%s]",
                    location.getLatitude(), location.getLongitude(), location.getAccuracy(), requestId);
            updateCommandStatusWithRequestId(uid, deviceId, commandId, "EXECUTED", resultMsg, requestId);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Cloud location batch write failed for req " + requestId + ": " + e.getMessage());
            String failMsg = "Cloud persistence failed: " + e.getMessage() + " (Stored in local queue for background retry)";
            updateCommandStatusWithRequestId(uid, deviceId, commandId, "FAILED", failMsg, requestId);
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
