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
import com.example.securetrack.database.SecurityAlertEntity;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.sync.DurableSyncManager;
import com.example.securetrack.utils.DeviceUtils;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService backgroundExecutor;
    private ILocalPersistence localPersistence;
    private ICloudCommandBatchPersister cloudPersister;
    private IPairingRecoveryService pairingRecoveryService;
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
        this(context,
                FirebaseFirestore.getInstance(),
                new SessionManager(context),
                new LocationRepository(context),
                AppDatabase.getInstance(context),
                new RoomLocalPersistenceImpl(context),
                new FirestoreCloudCommandBatchPersister(FirebaseFirestore.getInstance()),
                new FirestorePairingRecoveryService(FirebaseFirestore.getInstance()),
                Executors.newSingleThreadExecutor());
    }

    CommandExecutionManager(Context context,
                            FirebaseFirestore db,
                            SessionManager sessionManager,
                            LocationRepository locationRepository,
                            AppDatabase appDatabase,
                            ILocalPersistence localPersistence,
                            ICloudCommandBatchPersister cloudPersister,
                            IPairingRecoveryService pairingRecoveryService,
                            ExecutorService backgroundExecutor) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.db = db;
        this.sessionManager = sessionManager;
        this.locationRepository = locationRepository;
        if (this.context != null) {
            this.locationProvider = new com.example.securetrack.location.FusedLocationProviderImpl(this.context);
        } else {
            this.locationProvider = null;
        }
        this.appDatabase = appDatabase;
        this.localPersistence = localPersistence != null ? localPersistence : (context != null ? new RoomLocalPersistenceImpl(context) : null);
        this.cloudPersister = cloudPersister != null ? cloudPersister : (db != null ? new FirestoreCloudCommandBatchPersister(db) : null);
        this.pairingRecoveryService = pairingRecoveryService != null ? pairingRecoveryService : (db != null ? new FirestorePairingRecoveryService(db) : null);
        this.backgroundExecutor = backgroundExecutor != null ? backgroundExecutor : Executors.newSingleThreadExecutor();
    }

    public void setLocationProvider(com.example.securetrack.location.ISecureLocationProvider provider) {
        this.locationProvider = provider;
    }

    void setLocalPersistence(ILocalPersistence localPersistence) {
        this.localPersistence = localPersistence;
    }

    void setCloudPersister(ICloudCommandBatchPersister cloudPersister) {
        this.cloudPersister = cloudPersister;
    }

    void setPairingRecoveryService(IPairingRecoveryService pairingRecoveryService) {
        this.pairingRecoveryService = pairingRecoveryService;
    }

    ExecutorService getBackgroundExecutor() {
        return backgroundExecutor;
    }

    AppDatabase getAppDatabase() {
        return appDatabase;
    }

    ILocalPersistence getLocalPersistence() {
        return localPersistence;
    }

    ICloudCommandBatchPersister getCloudPersister() {
        return cloudPersister;
    }

    IPairingRecoveryService getPairingRecoveryService() {
        return pairingRecoveryService;
    }

    void clearProcessedCommandIds() {
        processedCommandIds.clear();
    }

    boolean isCommandProcessed(String commandId) {
        return processedCommandIds.contains(commandId);
    }

    private String getUid() {
        if (sessionManager.isPairedSecondaryDevice()) {
            String pairedOwnerUid = sessionManager.getPairedOwnerUid();
            if (pairedOwnerUid != null && !pairedOwnerUid.isEmpty()) {
                return pairedOwnerUid;
            }
        }
        FirebaseAuth auth = null;
        try {
            auth = FirebaseAuth.getInstance();
        } catch (Exception ignored) {}
        if (auth != null && auth.getCurrentUser() != null) {
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

    /**
     * Data class holding pairing recovery evaluation details.
     */
    public static class PairingRecoveryResult {
        public final boolean success;
        public final String ownerUid;
        public final String deviceName;
        public final String bleSharedSecret;
        public final String rejectionReason;

        public PairingRecoveryResult(boolean success, String ownerUid, String deviceName, String bleSharedSecret, String rejectionReason) {
            this.success = success;
            this.ownerUid = ownerUid;
            this.deviceName = deviceName;
            this.bleSharedSecret = bleSharedSecret;
            this.rejectionReason = rejectionReason;
        }

        public static PairingRecoveryResult success(String ownerUid, String deviceName, String bleSharedSecret) {
            return new PairingRecoveryResult(true, ownerUid, deviceName, bleSharedSecret, null);
        }

        public static PairingRecoveryResult reject(String reason) {
            return new PairingRecoveryResult(false, null, null, null, reason);
        }
    }

    /**
     * Pure evaluator for Google-authenticated pairing recovery.
     * Evaluates all candidate invitations against the 10 security criteria:
     * 1. Status is APPROVED.
     * 2. ClaimedByUid matches authenticated UID.
     * 3. ClaimedDeviceId matches installation ID.
     * 4. Valid ownerUid distinct from claimant (proper direction).
     * 5. Target device document exists.
     * 6. Target device is not REVOKED.
     * 7. Target device matches installation identity and auth identity.
     * 8. Constant-time credentials check (bleSharedSecret) against expected secret if provided.
     * 9. Resolves multiple duplicate invitations under the same verified owner.
     * 10. Rejects multiple conflicting owners with explicit conflict error.
     */
    public static PairingRecoveryResult evaluateRecovery(
            String currentAuthUid,
            String currentInstallationId,
            List<Map<String, Object>> approvedInvitations,
            Map<String, Object> deviceDocument
    ) {
        return evaluateRecovery(currentAuthUid, currentInstallationId, approvedInvitations, deviceDocument, null);
    }

    public static PairingRecoveryResult evaluateRecovery(
            String currentAuthUid,
            String currentInstallationId,
            List<Map<String, Object>> approvedInvitations,
            Map<String, Object> deviceDocument,
            String expectedBleSecret
    ) {
        Map<String, Map<String, Object>> docsByOwner = new HashMap<>();
        if (approvedInvitations != null) {
            for (Map<String, Object> inv : approvedInvitations) {
                if (inv != null) {
                    String oUid = (String) inv.get("ownerUid");
                    if (oUid != null && !oUid.trim().isEmpty() && deviceDocument != null) {
                        String docOwner = (String) deviceDocument.get("ownerUid");
                        if (docOwner == null || docOwner.equals(oUid)) {
                            docsByOwner.put(oUid, deviceDocument);
                        }
                    }
                }
            }
        }
        return evaluateRecoveryMultiOwner(currentAuthUid, currentInstallationId, approvedInvitations, docsByOwner, expectedBleSecret);
    }

    public static PairingRecoveryResult evaluateRecoveryMultiOwner(
            String currentAuthUid,
            String currentInstallationId,
            List<Map<String, Object>> approvedInvitations,
            Map<String, Map<String, Object>> deviceDocumentsByOwner,
            String expectedBleSecret
    ) {
        if (currentAuthUid == null || currentAuthUid.trim().isEmpty()) {
            return PairingRecoveryResult.reject("Authenticated UID is missing or empty");
        }
        if (currentInstallationId == null || currentInstallationId.trim().isEmpty()) {
            return PairingRecoveryResult.reject("Installation ID is missing or empty");
        }
        if (approvedInvitations == null || approvedInvitations.isEmpty()) {
            return PairingRecoveryResult.reject("No approved invitations found");
        }

        List<CandidateMatch> validCandidates = new ArrayList<>();
        String lastRejectionReason = null;

        for (Map<String, Object> inv : approvedInvitations) {
            if (inv == null) continue;

            String status = (String) inv.get("status");
            if (!"APPROVED".equalsIgnoreCase(status)) {
                lastRejectionReason = "Invitation status is not APPROVED: " + status;
                continue;
            }

            String claimedByUid = (String) inv.get("claimedByUid");
            if (!currentAuthUid.equals(claimedByUid)) {
                lastRejectionReason = "Authenticated UID mismatch: expected " + currentAuthUid + ", found " + claimedByUid;
                continue;
            }

            String claimedDeviceId = (String) inv.get("claimedDeviceId");
            if (!currentInstallationId.equals(claimedDeviceId)) {
                lastRejectionReason = "Installation ID mismatch: expected " + currentInstallationId + ", found " + claimedDeviceId;
                continue;
            }

            String ownerUid = (String) inv.get("ownerUid");
            if (ownerUid == null || ownerUid.trim().isEmpty()) {
                lastRejectionReason = "Missing or empty ownerUid in invitation";
                continue;
            }

            // Direction verification: owner cannot be the claimant device itself
            if (ownerUid.equals(currentAuthUid)) {
                lastRejectionReason = "Incorrect owner/target direction: ownerUid cannot match claiming device UID";
                continue;
            }

            // Device document verification
            Map<String, Object> devDoc = deviceDocumentsByOwner != null ? deviceDocumentsByOwner.get(ownerUid) : null;
            if (devDoc == null) {
                lastRejectionReason = "Device document does not exist under owner " + com.example.securetrack.utils.DeviceUtils.redactIdentifier(ownerUid);
                continue;
            }

            String devStatus = (String) devDoc.get("status");
            if ("REVOKED".equalsIgnoreCase(devStatus)) {
                lastRejectionReason = "Device is REVOKED in Firestore under owner " + com.example.securetrack.utils.DeviceUtils.redactIdentifier(ownerUid);
                continue;
            }

            // Verify installation ID and deviceAuthUid on device doc if present
            String devInstallId = (String) devDoc.get("installationId");
            if (devInstallId == null) devInstallId = (String) devDoc.get("id");
            if (devInstallId != null && !currentInstallationId.equals(devInstallId)) {
                lastRejectionReason = "Device document installationId mismatch: expected " + currentInstallationId + ", found " + devInstallId;
                continue;
            }

            String devAuthUid = (String) devDoc.get("deviceAuthUid");
            if (devAuthUid != null && !currentAuthUid.equals(devAuthUid)) {
                lastRejectionReason = "Device document deviceAuthUid mismatch: expected " + currentAuthUid + ", found " + devAuthUid;
                continue;
            }

            String invSecret = (String) inv.get("bleSharedSecret");
            String devSecret = (String) devDoc.get("bleSharedSecret");

            // If both invitation and device document have secrets, verify constant-time match
            if (invSecret != null && devSecret != null) {
                if (!com.example.securetrack.utils.DeviceUtils.constantTimeEquals(invSecret, devSecret)) {
                    lastRejectionReason = "Pairing credentials mismatch between invitation and device document";
                    continue;
                }
            }

            // If expectedBleSecret is required, verify match
            if (expectedBleSecret != null && !expectedBleSecret.trim().isEmpty()) {
                String candidateSecret = invSecret != null ? invSecret : devSecret;
                if (candidateSecret == null || !com.example.securetrack.utils.DeviceUtils.constantTimeEquals(expectedBleSecret, candidateSecret)) {
                    lastRejectionReason = "Pairing credentials / shared secret mismatch with local session";
                    continue;
                }
            }

            String devName = (String) devDoc.get("deviceName");
            if (devName == null || devName.trim().isEmpty()) {
                devName = (String) devDoc.get("model");
            }
            if (devName == null || devName.trim().isEmpty()) {
                devName = "Paired Android Device";
            }

            String effectiveSecret = invSecret != null ? invSecret : devSecret;
            validCandidates.add(new CandidateMatch(ownerUid, devName, effectiveSecret));
        }

        if (validCandidates.isEmpty()) {
            return PairingRecoveryResult.reject(lastRejectionReason != null
                    ? lastRejectionReason
                    : "No verified candidate invitations found matching target security policy");
        }

        // Check for multiple conflicting owners
        Set<String> distinctOwners = new HashSet<>();
        for (CandidateMatch candidate : validCandidates) {
            distinctOwners.add(candidate.ownerUid);
        }

        if (distinctOwners.size() > 1) {
            return PairingRecoveryResult.reject("Ownership conflict: Multiple conflicting owners claimed device. Requires fresh authorized pairing.");
        }

        // All valid candidates share the exact same verified owner!
        // Select candidate (prefer candidate with non-null bleSharedSecret if available)
        CandidateMatch selected = validCandidates.get(0);
        for (CandidateMatch candidate : validCandidates) {
            if (candidate.bleSecret != null && !candidate.bleSecret.isEmpty()) {
                selected = candidate;
                break;
            }
        }

        return PairingRecoveryResult.success(selected.ownerUid, selected.deviceName, selected.bleSecret);
    }

    private static class CandidateMatch {
        final String ownerUid;
        final String deviceName;
        final String bleSecret;

        CandidateMatch(String ownerUid, String deviceName, String bleSecret) {
            this.ownerUid = ownerUid;
            this.deviceName = deviceName;
            this.bleSecret = bleSecret;
        }
    }

    /**
     * Validates whether command's targetDeviceId matches the deviceId from the document path.
     * Preserves backward compatibility with legitimate older commands that do not contain targetDeviceId.
     */
    public static boolean validateCommandTarget(String documentDeviceId, String commandTargetDeviceId) {
        if (commandTargetDeviceId == null || commandTargetDeviceId.trim().isEmpty()) {
            return true; // Backward compatibility with older commands
        }
        return commandTargetDeviceId.equals(documentDeviceId);
    }

    public synchronized void startListeningToCommands() {
        stopListeningToCommands();

        FirebaseAuth auth = null;
        try {
            auth = FirebaseAuth.getInstance();
        } catch (Exception ignored) {}
        String currentInstallationId = sessionManager.getInstallationId();

        String currentAuthUid = null;
        if (auth != null && auth.getCurrentUser() != null) {
            currentAuthUid = auth.getCurrentUser().getUid();
        } else if (sessionManager.getFirebaseUid() != null) {
            currentAuthUid = sessionManager.getFirebaseUid();
        }

        // Exact pairing recovery: Self-heal secondary enrollment using exact claimedDeviceId + authUid + status
        if (currentAuthUid != null && !sessionManager.isPairedSecondaryDevice()) {
            if (currentInstallationId == null || currentInstallationId.isEmpty()) {
                Log.w(TAG, "Cannot attempt pairing recovery: missing installationId");
                return;
            }

            Log.d(TAG, "Checking pairing recovery for authUid=" + currentAuthUid + ", installationId=" + currentInstallationId);

            pairingRecoveryService.checkRecovery(currentAuthUid, currentInstallationId, sessionManager.getBleSharedSecret(), new IPairingRecoveryService.RecoveryCallback() {
                @Override
                public void onRecoverySuccess(String ownerUid, String deviceName, String bleSecret) {
                    Log.i(TAG, "Self-healing pairing state verified for installation " + currentInstallationId + " under owner " + ownerUid);
                    sessionManager.setPairedSecondaryDevice(true);
                    sessionManager.setPairedOwnerUid(ownerUid);
                    sessionManager.setOwnerUid(ownerUid);
                    sessionManager.setCurrentDeviceId(currentInstallationId);

                    if (deviceName != null && !deviceName.isEmpty()) {
                        sessionManager.setDeviceName(deviceName);
                    }

                    if (bleSecret != null && !bleSecret.isEmpty()) {
                        sessionManager.setBleSharedSecret(bleSecret);
                    }

                    startListeningToCommandsInternal();
                }

                @Override
                public void onRecoveryRejected(String reason) {
                    Log.w(TAG, "Pairing recovery rejected: " + reason + ". Command listener will NOT start under secondary UID.");
                    // BLOCKED: Do NOT start command listener under wrong UID!
                }

                @Override
                public void onRecoveryError(Exception exception) {
                    Log.e(TAG, "Pairing recovery check failed. Command listener will NOT start under wrong UID.", exception);
                    // BLOCKED: Do NOT start command listener under wrong UID!
                }

                @Override
                public void onNoSecondaryInvitation() {
                    Log.d(TAG, "No matching approved pairing invitation for installation " + currentInstallationId + ". Starting as primary/standalone.");
                    startListeningToCommandsInternal();
                }
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
        if (context != null) {
            try {
                BleNearbyManager.getInstance(context).startAdvertisingAsTarget();
            } catch (Exception e) {
                Log.w(TAG, "Failed to start BLE advertising: " + e.getMessage());
            }
        }

        if (db == null) {
            Log.d(TAG, "Firestore db instance is null; skipping remote command listeners (unit test mode)");
            return;
        }

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

    static class TxResult {
        final String action;
        final DocumentSnapshot snapshot;
        TxResult(String action, DocumentSnapshot snapshot) {
            this.action = action;
            this.snapshot = snapshot;
        }
    }

    /**
     * Genuinely Atomic Command Claiming via Firestore Transaction with Robust Semantics.
     * Verifies targetDeviceId against the path deviceId before claiming.
     */
    void executeSingleCommand(String uid, String deviceId, DocumentSnapshot initialDoc) {
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
                return new TxResult("NOT_FOUND", null);
            }

            String currentStatus = snapshot.getString("status");
            if (!"QUEUED".equalsIgnoreCase(currentStatus)) {
                return new TxResult("ALREADY_CLAIMED", snapshot);
            }

            // Target identity validation
            String targetDeviceId = snapshot.getString("targetDeviceId");
            if (!validateCommandTarget(deviceId, targetDeviceId)) {
                Log.w(TAG, "Target device ID mismatch for command " + commandId + ": expected " + deviceId + " but found " + targetDeviceId);
                Map<String, Object> failUpdates = new HashMap<>();
                failUpdates.put("status", "FAILED");
                failUpdates.put("result", "Target device mismatch: expected " + deviceId + " but command target was " + targetDeviceId);
                failUpdates.put("executedAt", FieldValue.serverTimestamp());
                transaction.update(cmdRef, failUpdates);
                return new TxResult("TARGET_MISMATCH", snapshot);
            }

            String type = snapshot.getString("commandType");
            if (type == null) {
                type = snapshot.getString("command");
            }
            if (type == null) {
                Map<String, Object> failUpdates = new HashMap<>();
                failUpdates.put("status", "FAILED");
                failUpdates.put("result", "Malformed command missing commandType");
                failUpdates.put("executedAt", FieldValue.serverTimestamp());
                transaction.update(cmdRef, failUpdates);
                return new TxResult("MALFORMED", snapshot);
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
                    return new TxResult("EXPIRED", snapshot);
                }
            }

            Map<String, Object> claimUpdates = new HashMap<>();
            claimUpdates.put("status", "EXECUTING");
            claimUpdates.put("claimedAt", FieldValue.serverTimestamp());
            transaction.update(cmdRef, claimUpdates);

            return new TxResult("CLAIMED", snapshot);
        }).addOnSuccessListener(txResult -> {
            if (txResult == null) return;
            switch (txResult.action) {
                case "CLAIMED":
                    processedCommandIds.add(commandId);
                    executeClaimedCommand(uid, deviceId, commandId, txResult.snapshot);
                    break;
                case "TARGET_MISMATCH":
                    Log.w(TAG, "Command " + commandId + " rejected: targetDeviceId mismatch.");
                    processedCommandIds.add(commandId);
                    backgroundExecutor.execute(() -> {
                        try {
                            localPersistence.updateCommandExecution(
                                    commandId,
                                    "FAILED",
                                    "Target device mismatch",
                                    System.currentTimeMillis(),
                                    "SYNCED"
                            );
                        } catch (Exception ignored) {}
                    });
                    break;
                case "EXPIRED":
                    Log.w(TAG, "Command " + commandId + " marked as EXPIRED in committed transaction.");
                    processedCommandIds.add(commandId);
                    break;
                case "MALFORMED":
                    Log.w(TAG, "Command " + commandId + " marked as FAILED (malformed) in committed transaction.");
                    processedCommandIds.add(commandId);
                    break;
                case "ALREADY_CLAIMED":
                case "NOT_FOUND":
                    Log.d(TAG, "Command " + commandId + " status was not QUEUED or not found (" + txResult.action + ").");
                    processedCommandIds.add(commandId);
                    break;
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Command claim transaction failed for " + commandId + ": " + e.getMessage());
        });
    }

    private void executeClaimedCommand(String uid, String deviceId, String commandId, DocumentSnapshot claimedDoc) {
        String type = claimedDoc.getString("commandType");
        if (type == null) type = claimedDoc.getString("command");

        Log.i(TAG, "Successfully claimed and executing remote command " + commandId + " [" + type + "] on target " + deviceId);

        final String finalType = type;
        com.google.firebase.Timestamp createdAt = claimedDoc.getTimestamp("createdAt");
        long maxAge = ("LOCK_DEVICE".equalsIgnoreCase(type) || "ALARM_ALERT".equalsIgnoreCase(type))
                ? EXPIRY_LOCK_ALARM_MS : EXPIRY_LOCATE_MS;

        backgroundExecutor.execute(() -> {
            try {
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
                localPersistence.insertOrUpdateCommand(entity);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record claimed command in Room: " + commandId, e);
            }
        });

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
     * Confirmed Durable Location Persistence:
     * 1. Room insertion executed on background executor.
     * 2. Successful Room insertion verified before initiating cloud persistence.
     * 3. Handles and logs local database insertion failures explicitly.
     * 4. Single atomic Firestore WriteBatch coordinates location history, device telemetry, and command terminal status.
     * 5. Room records marked synced ONLY after confirmed cloud persistence.
     * 6. Unsynced local records preserved when cloud persistence fails.
     * 7. Terminal status FAILED safely applied upon cloud error so command never remains stuck in EXECUTING.
     * 8. Avoids blocking the Android main thread.
     */
    void recordAndCompleteLocate(String uid, String deviceId, String commandId, String requestId, Location location) {
        long locTime = (location != null && location.getTime() > 0) ? location.getTime() : System.currentTimeMillis();
        double lat = location != null ? location.getLatitude() : 0.0;
        double lng = location != null ? location.getLongitude() : 0.0;
        float acc = location != null ? location.getAccuracy() : 0.0f;
        double alt = location != null ? location.getAltitude() : 0.0;
        float spd = location != null ? location.getSpeed() : 0.0f;
        recordAndCompleteLocate(uid, deviceId, commandId, requestId, lat, lng, acc, alt, spd, locTime);
    }

    void recordAndCompleteLocate(String uid, String deviceId, String commandId, String requestId,
                                 double lat, double lng, float acc, double alt, float spd, long locTime) {
        backgroundExecutor.execute(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String timestampStr = sdf.format(new Date(locTime > 0 ? locTime : System.currentTimeMillis()));

            long localRowId;
            try {
                LocationEntity existing = localPersistence.getLocationByClientUuid(requestId);
                if (existing != null) {
                    localRowId = existing.getId();
                    Log.i(TAG, "Duplicate location request detected for requestId " + requestId + ", reusing existing local row id: " + localRowId);
                } else {
                    LocationEntity entity = new LocationEntity(
                            deviceId,
                            requestId,
                            lat,
                            lng,
                            acc,
                            alt,
                            spd,
                            timestampStr,
                            false
                    );
                    localRowId = localPersistence.insertLocation(entity);
                }
            } catch (Exception e) {
                Log.e(TAG, "Local Room database insertion failed for req " + requestId, e);
                localRowId = -1;
            }

            // Confirm successful Room insertion before starting cloud persistence
            if (localRowId <= 0) {
                Log.e(TAG, "Local database insertion failed or returned invalid row ID (" + localRowId + ") for req " + requestId);
                String failMsg = "Local location persistence failed: database insert error";
                updateCommandStatusWithRequestId(uid, deviceId, commandId, "FAILED", failMsg, requestId);
                return;
            }

            final long finalLocalRowId = localRowId;

            // Prepare atomic Firestore WriteBatch
            com.google.firebase.Timestamp gpsTimestamp = LocationRepository.parseTimestamp(timestampStr);

            Map<String, Object> locationMap = new HashMap<>();
            locationMap.put("clientUuid", requestId);
            locationMap.put("requestId", requestId);
            locationMap.put("latitude", lat);
            locationMap.put("longitude", lng);
            locationMap.put("accuracy", acc);
            locationMap.put("altitude", alt);
            locationMap.put("speed", spd);
            locationMap.put("source", "GPS");
            locationMap.put("timestamp", gpsTimestamp);
            locationMap.put("uploadedAt", FieldValue.serverTimestamp());

            Map<String, Object> devUpdates = new HashMap<>();
            devUpdates.put("lastSeen", FieldValue.serverTimestamp());
            if (context != null) {
                devUpdates.put("batteryLevel", DeviceUtils.getBatteryLevel(context));
            } else {
                devUpdates.put("batteryLevel", 100);
            }
            devUpdates.put("locationStatus", "ACTIVE");
            devUpdates.put("status", "ONLINE");
            devUpdates.put("lastLatitude", lat);
            devUpdates.put("lastLongitude", lng);
            devUpdates.put("lastAccuracy", acc);
            devUpdates.put("locationAccuracy", acc);
            devUpdates.put("locationSource", "GPS");
            devUpdates.put("locationTimestamp", gpsTimestamp);
            devUpdates.put("lastRequestId", requestId);

            String resultMsg = String.format(Locale.US, "Location: (%.5f, %.5f) ±%.1fm [req:%s]",
                    lat, lng, acc, requestId);

            Map<String, Object> cmdUpdates = new HashMap<>();
            cmdUpdates.put("status", "EXECUTED");
            cmdUpdates.put("result", resultMsg);
            cmdUpdates.put("requestId", requestId);
            cmdUpdates.put("executedAt", FieldValue.serverTimestamp());

            cloudPersister.commitLocateBatch(uid, deviceId, commandId, requestId, locationMap, devUpdates, cmdUpdates, new ICloudCommandBatchPersister.Callback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Location history, device telemetry, and command status successfully committed to cloud for req: " + requestId);
                    backgroundExecutor.execute(() -> {
                        try {
                            localPersistence.markLocationSynced(finalLocalRowId);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to mark location as synced in Room: " + finalLocalRowId, e);
                        }
                        if (commandId != null && !commandId.isEmpty()) {
                            try {
                                localPersistence.updateCommandExecution(commandId, "EXECUTED", resultMsg, System.currentTimeMillis(), "SYNCED");
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to update command execution status in Room: " + commandId, e);
                            }
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Cloud location batch write failed for req " + requestId + ": " + (e != null ? e.getMessage() : "Unknown error"));
                    // Preserve unsynced local location record (isSynced remains false)
                    String failMsg = "Cloud persistence failed: " + (e != null ? e.getMessage() : "Network error")
                            + " (Stored in local queue for background retry)";
                    if (commandId != null && !commandId.isEmpty()) {
                        updateCommandStatusWithRequestId(uid, deviceId, commandId, "FAILED", failMsg, requestId);
                    }
                }
            });
        });
    }

    void updateCommandStatusWithRequestId(String uid, String deviceId, String commandId, String status, String result, String requestId) {
        if (commandId == null || commandId.isEmpty()) return;

        long now = System.currentTimeMillis();

        // 1. Immediately persist pending terminal status in local database with syncStatus = "PENDING"
        backgroundExecutor.execute(() -> {
            try {
                localPersistence.updateCommandExecution(commandId, status, result, now, "PENDING");
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist pending command status locally for " + commandId, e);
            }
        });

        // 2. Prepare payload for cloud
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("result", result);
        if (requestId != null) {
            updates.put("requestId", requestId);
        }
        updates.put("executedAt", FieldValue.serverTimestamp());

        // 3. Attempt Firestore cloud sync
        cloudPersister.updateCommandStatus(uid, deviceId, commandId, updates, new ICloudCommandBatchPersister.Callback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Command " + commandId + " updated to " + status + " (" + result + ") confirmed by cloud");
                backgroundExecutor.execute(() -> {
                    try {
                        localPersistence.updateCommandSyncStatus(commandId, "SYNCED");
                    } catch (Exception ignored) {}
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.w(TAG, "Failed to update command " + commandId + " terminal status to cloud: "
                        + (e != null ? e.getMessage() : "network error") + ". syncStatus remains PENDING; scheduling background retry.");
                // Retains syncStatus = "PENDING" locally!
                // Trigger reliable WorkManager background retry!
                if (context != null) {
                    try {
                        DurableSyncManager.triggerImmediateSync(context);
                    } catch (Exception ex) {
                        Log.w(TAG, "Could not trigger immediate sync via WorkManager", ex);
                    }
                }
            }
        });
    }

    void updateCommandStatus(String uid, String deviceId, String commandId, String status, String result) {
        updateCommandStatusWithRequestId(uid, deviceId, commandId, status, result, null);
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

                backgroundExecutor.execute(() -> {
                    try {
                        SecurityAlertEntity entity = new SecurityAlertEntity(
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
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to record security alert in Room: " + alertId, e);
                    }
                });

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

    public synchronized void stopListeningToCommands() {
        if (commandListener != null) {
            commandListener.remove();
            commandListener = null;
        }
        if (context != null) {
            try {
                BleNearbyManager.getInstance(context).stopAdvertising();
            } catch (Exception e) {
                Log.w(TAG, "Failed to stop BLE advertising: " + e.getMessage());
            }
        }
    }
}
