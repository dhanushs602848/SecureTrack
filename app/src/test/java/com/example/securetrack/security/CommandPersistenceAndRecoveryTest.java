package com.example.securetrack.security;

import android.content.SharedPreferences;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.utils.SessionManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Real Behavioral Test Suite for SecureTrack Reliability, Location Persistence & Failure Recovery.
 * Invokes the actual production orchestration methods with deterministic fakes to verify:
 * - Room insertion failure prevents cloud upload.
 * - Successful insertion allows cloud upload.
 * - Firestore batch failure preserves unsynced Room records and persists pending terminal status.
 * - Firestore batch failure combined with terminal write failure retains PENDING sync status for WorkManager.
 * - Firestore batch success marks Room records synced and transitions command to EXECUTED.
 * - Duplicate request IDs do not create duplicate location records.
 * - Command status changes to EXECUTED only after confirmed cloud persistence.
 * - Command target mismatch prevents execution; legacy commands remain compatible.
 * - Pairing recovery rejects mismatched UID, installation ID, ambiguous invitations, missing device docs, and revoked devices.
 * - Listener startup is blocked until secondary pairing is verified.
 */
public class CommandPersistenceAndRecoveryTest {

    private InMemorySharedPreferences inMemoryPrefs;
    private SessionManager sessionManager;
    private FakeLocalPersistence fakeLocal;
    private FakeCloudCommandBatchPersister fakeCloud;
    private FakePairingRecoveryService fakeRecovery;
    private DirectExecutor directExecutor;
    private CommandExecutionManager manager;

    @Before
    public void setUp() {
        inMemoryPrefs = new InMemorySharedPreferences();
        sessionManager = new SessionManager(inMemoryPrefs);
        sessionManager.clearAll();

        fakeLocal = new FakeLocalPersistence();
        fakeCloud = new FakeCloudCommandBatchPersister();
        fakeRecovery = new FakePairingRecoveryService();
        directExecutor = new DirectExecutor();

        manager = new CommandExecutionManager(
                null,
                null, // FirebaseFirestore not used by fakes
                sessionManager,
                null, // LocationRepository
                null, // AppDatabase
                fakeLocal,
                fakeCloud,
                fakeRecovery,
                directExecutor
        );
    }

    // =========================================================================
    // 1. LOCAL INSERTION FAILURE PREVENTS CLOUD UPLOAD
    // =========================================================================

    @Test
    public void testActualRoomInsertionFailurePreventsCloudUpload() {
        fakeLocal.shouldFailLocationInsert = true;

        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId = "cmd-locate-fail-insert";
        String requestId = "req-fail-001";

        // Pre-seed claimed command in local persistence
        fakeLocal.insertOrUpdateCommand(new CommandEntity(
                commandId, deviceId, "LOCATE_NOW", uid, "",
                "EXECUTING", "", "CLOUD", System.currentTimeMillis(),
                System.currentTimeMillis() + 60000, 0, 0, "SYNCED", 0
        ));

        // Invoke production method with coordinates
        manager.recordAndCompleteLocate(uid, deviceId, commandId, requestId, 12.9716, 77.5946, 5.0f, 0.0, 0.0f, System.currentTimeMillis());

        // Verify cloud batch was NEVER attempted
        assertEquals("Cloud batch must NOT be initiated when local Room insert fails",
                0, fakeCloud.batchCommitCount.get());

        // Verify local command was updated to FAILED with database error
        CommandEntity updatedCmd = fakeLocal.getCommandById(commandId);
        assertNotNull(updatedCmd);
        assertEquals("FAILED", updatedCmd.getStatus());
        assertTrue("Result message must explicitly indicate local database error",
                updatedCmd.getResult().contains("Local location persistence failed"));
    }

    // =========================================================================
    // 2. SUCCESSFUL INSERTION ALLOWS CLOUD UPLOAD
    // =========================================================================

    @Test
    public void testActualSuccessfulInsertionAllowsCloudUpload() {
        fakeLocal.shouldFailLocationInsert = false;

        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId = "cmd-locate-success";
        String requestId = "req-success-001";

        fakeLocal.insertOrUpdateCommand(new CommandEntity(
                commandId, deviceId, "LOCATE_NOW", uid, "",
                "EXECUTING", "", "CLOUD", System.currentTimeMillis(),
                System.currentTimeMillis() + 60000, 0, 0, "SYNCED", 0
        ));

        manager.recordAndCompleteLocate(uid, deviceId, commandId, requestId, 12.9716, 77.5946, 4.5f, 0.0, 0.0f, System.currentTimeMillis());

        // Verify cloud batch was executed
        assertEquals("Cloud batch must be executed upon successful local insertion",
                1, fakeCloud.batchCommitCount.get());

        // Verify payload submitted to cloud
        Map<String, Object> submittedLoc = fakeCloud.lastLocationMap;
        assertNotNull(submittedLoc);
        assertEquals(requestId, submittedLoc.get("requestId"));
        assertEquals(12.9716, (double) submittedLoc.get("latitude"), 0.0001);
        assertEquals(77.5946, (double) submittedLoc.get("longitude"), 0.0001);

        // Verify Room record is marked as synced
        LocationEntity entity = fakeLocal.getLocationByClientUuid(requestId);
        assertNotNull(entity);
        assertTrue("Local entity must be marked isSynced = true after confirmed cloud batch", entity.isSynced());

        // Verify command terminal status EXECUTED
        CommandEntity cmd = fakeLocal.getCommandById(commandId);
        assertEquals("EXECUTED", cmd.getStatus());
        assertEquals("SYNCED", cmd.getSyncStatus());
    }

    // =========================================================================
    // 3. FIRESTORE BATCH FAILURE PRESERVES UNSYNCED ROOM RECORD
    // =========================================================================

    @Test
    public void testFirestoreBatchFailurePreservesUnsyncedRoomRecord() {
        fakeCloud.shouldFailBatch = true; // Cloud write will fail

        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId = "cmd-locate-batch-fail";
        String requestId = "req-cloud-fail-001";

        fakeLocal.insertOrUpdateCommand(new CommandEntity(
                commandId, deviceId, "LOCATE_NOW", uid, "",
                "EXECUTING", "", "CLOUD", System.currentTimeMillis(),
                System.currentTimeMillis() + 60000, 0, 0, "SYNCED", 0
        ));

        manager.recordAndCompleteLocate(uid, deviceId, commandId, requestId, 12.9716, 77.5946, 6.0f, 0.0, 0.0f, System.currentTimeMillis());

        // Verify local entity survives with isSynced = false
        LocationEntity entity = fakeLocal.getLocationByClientUuid(requestId);
        assertNotNull("Local location record must survive", entity);
        assertFalse("Local location record must retain isSynced = false for background retry", entity.isSynced());
        assertEquals("markLocationSynced must not be called upon cloud failure",
                0, fakeLocal.locationSyncMarkCount.get());

        // Verify command transitions to FAILED with retry notice so it does not remain stuck in EXECUTING
        CommandEntity cmd = fakeLocal.getCommandById(commandId);
        assertNotNull(cmd);
        assertEquals("FAILED", cmd.getStatus());
        assertTrue("Result must note offline queue retention", cmd.getResult().contains("Stored in local queue for background retry"));
    }

    // =========================================================================
    // 4. BATCH FAILURE AND SUBSEQUENT TERMINAL STATUS WRITE FAILURE
    // =========================================================================

    @Test
    public void testBatchFailureAndSubsequentTerminalWriteFailureRetainsPendingSync() {
        fakeCloud.shouldFailBatch = true;
        fakeCloud.shouldFailCommandStatusUpdate = true; // Subsequent status write to cloud also fails

        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId = "cmd-both-fail";
        String requestId = "req-both-fail-001";

        fakeLocal.insertOrUpdateCommand(new CommandEntity(
                commandId, deviceId, "LOCATE_NOW", uid, "",
                "EXECUTING", "", "CLOUD", System.currentTimeMillis(),
                System.currentTimeMillis() + 60000, 0, 0, "SYNCED", 0
        ));

        manager.recordAndCompleteLocate(uid, deviceId, commandId, requestId, 12.9716, 77.5946, 5.0f, 0.0, 0.0f, System.currentTimeMillis());

        // 1. Unsynced local location record is preserved
        LocationEntity entity = fakeLocal.getLocationByClientUuid(requestId);
        assertNotNull(entity);
        assertFalse(entity.isSynced());

        // 2. Command status is recorded locally as FAILED with syncStatus = PENDING
        CommandEntity cmd = fakeLocal.getCommandById(commandId);
        assertNotNull(cmd);
        assertEquals("FAILED", cmd.getStatus());
        assertEquals("PENDING", cmd.getSyncStatus());

        // 3. Command is present in getPendingSyncCommands() for WorkManager durable recovery
        List<CommandEntity> pending = fakeLocal.getPendingSyncCommands();
        assertTrue("Failed command must be queued in pending sync commands for WorkManager",
                pending.stream().anyMatch(c -> c.getCommandId().equals(commandId)));
    }

    // =========================================================================
    // 5. FIRESTORE BATCH SUCCESS MARKS CORRECT ROOM RECORD SYNCED
    // =========================================================================

    @Test
    public void testFirestoreBatchSuccessMarksCorrectRoomRecordSynced() {
        fakeCloud.shouldFailBatch = false;

        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId = "cmd-locate-batch-success";
        String requestId = "req-batch-ok-001";

        manager.recordAndCompleteLocate(uid, deviceId, commandId, requestId, 13.0827, 80.2707, 3.2f, 0.0, 0.0f, System.currentTimeMillis());

        LocationEntity entity = fakeLocal.getLocationByClientUuid(requestId);
        assertNotNull(entity);
        assertTrue(entity.isSynced());
        assertEquals(1, fakeLocal.locationSyncMarkCount.get());

        // Device telemetry and command updates were sent in batch
        assertNotNull(fakeCloud.lastDeviceUpdates);
        assertEquals("ONLINE", fakeCloud.lastDeviceUpdates.get("status"));
        assertEquals("ACTIVE", fakeCloud.lastDeviceUpdates.get("locationStatus"));

        assertNotNull(fakeCloud.lastCommandUpdates);
        assertEquals("EXECUTED", fakeCloud.lastCommandUpdates.get("status"));
    }

    // =========================================================================
    // 6. DUPLICATE REQUEST IDS DO NOT CREATE DUPLICATE LOCATION RECORDS
    // =========================================================================

    @Test
    public void testDuplicateRequestIdsDoNotCreateDuplicateLocationRecords() {
        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId1 = "cmd-locate-dup-1";
        String commandId2 = "cmd-locate-dup-2";
        String requestId = "req-dedup-constant-id";

        // First attempt inserts record
        manager.recordAndCompleteLocate(uid, deviceId, commandId1, requestId, 12.9716, 77.5946, 5.0f, 0.0, 0.0f, System.currentTimeMillis());
        assertEquals(1, fakeLocal.locationInsertCount.get());
        assertEquals(1, fakeLocal.getAllLocations().size());

        long initialRowId = fakeLocal.getLocationByClientUuid(requestId).getId();

        // Second attempt with identical requestId
        manager.recordAndCompleteLocate(uid, deviceId, commandId2, requestId, 12.9716, 77.5946, 5.0f, 0.0, 0.0f, System.currentTimeMillis());

        // Assert no second insertion occurred
        assertEquals("Duplicate requestId must not trigger another insert",
                1, fakeLocal.locationInsertCount.get());
        assertEquals("Total locations in table must remain exactly 1",
                1, fakeLocal.getAllLocations().size());
        assertEquals("Existing row ID must be reused",
                initialRowId, fakeLocal.getLocationByClientUuid(requestId).getId());
    }

    // =========================================================================
    // 7. COMMAND STATUS CHANGES ONLY AFTER CONFIRMED CLOUD PERSISTENCE
    // =========================================================================

    @Test
    public void testCommandStatusChangesOnlyAfterConfirmedCloudPersistence() {
        // Use a deferred batch persister that holds callback
        DeferredCloudBatchPersister deferredCloud = new DeferredCloudBatchPersister();
        manager.setCloudPersister(deferredCloud);

        String uid = "owner-uid-101";
        String deviceId = "dev-samsung-a07";
        String commandId = "cmd-defer-status";
        String requestId = "req-defer-001";

        fakeLocal.insertOrUpdateCommand(new CommandEntity(
                commandId, deviceId, "LOCATE_NOW", uid, "",
                "EXECUTING", "", "CLOUD", System.currentTimeMillis(),
                System.currentTimeMillis() + 60000, 0, 0, "SYNCED", 0
        ));

        manager.recordAndCompleteLocate(uid, deviceId, commandId, requestId, 12.9716, 77.5946, 5.0f, 0.0, 0.0f, System.currentTimeMillis());

        // Before cloud confirmation: command status in Room must still be EXECUTING
        CommandEntity beforeConfirmation = fakeLocal.getCommandById(commandId);
        assertNotEquals("Command status must NOT transition to EXECUTED before cloud confirms",
                "EXECUTED", beforeConfirmation.getStatus());

        // Cloud confirms commit
        deferredCloud.triggerSuccess();

        // After cloud confirmation: command status in Room transitions to EXECUTED
        CommandEntity afterConfirmation = fakeLocal.getCommandById(commandId);
        assertEquals("Command status must transition to EXECUTED upon confirmed cloud persistence",
                "EXECUTED", afterConfirmation.getStatus());
    }

    // =========================================================================
    // 8. COMMAND TARGET MISMATCH PREVENTS EXECUTION
    // =========================================================================

    @Test
    public void testCommandTargetMismatchPreventsExecution() {
        String documentPathDeviceId = "dev-samsung-a07-real";
        String commandTargetDeviceId = "dev-pixel-attacker";

        boolean isValid = CommandExecutionManager.validateCommandTarget(documentPathDeviceId, commandTargetDeviceId);
        assertFalse("Command with mismatched targetDeviceId must be rejected", isValid);
    }

    // =========================================================================
    // 9. LEGACY COMMANDS WITHOUT TARGETDEVICEID REMAIN COMPATIBLE
    // =========================================================================

    @Test
    public void testLegacyCommandsWithoutTargetDeviceIdRemainCompatible() {
        String documentPathDeviceId = "dev-samsung-a07-real";

        assertTrue("Null targetDeviceId must be accepted for legacy compatibility",
                CommandExecutionManager.validateCommandTarget(documentPathDeviceId, null));
        assertTrue("Empty targetDeviceId must be accepted for legacy compatibility",
                CommandExecutionManager.validateCommandTarget(documentPathDeviceId, ""));
        assertTrue("Whitespace targetDeviceId must be accepted for legacy compatibility",
                CommandExecutionManager.validateCommandTarget(documentPathDeviceId, "   "));
        assertTrue("Matching targetDeviceId must be accepted",
                CommandExecutionManager.validateCommandTarget(documentPathDeviceId, documentPathDeviceId));
    }

    // =========================================================================
    // 10. PAIRING RECOVERY EVALUATOR CONTRACT (12 COMPREHENSIVE SECURITY POLICIES)
    // =========================================================================

    @Test
    public void testPairingRecoveryEvaluator_RejectsAllInvalidConditions() {
        String authUid = "user-auth-123";
        String installId = "inst-001";
        String ownerUid = "owner-999";
        String bleSecret = "sec-123";

        // Case 1: One valid approved invitation
        List<Map<String, Object>> invs = new ArrayList<>();
        Map<String, Object> inv = new HashMap<>();
        inv.put("status", "APPROVED");
        inv.put("claimedByUid", authUid);
        inv.put("claimedDeviceId", installId);
        inv.put("ownerUid", ownerUid);
        inv.put("bleSharedSecret", bleSecret);
        invs.add(inv);

        Map<String, Object> devDoc = new HashMap<>();
        devDoc.put("status", "ONLINE");
        devDoc.put("deviceName", "Samsung A07");
        devDoc.put("ownerUid", ownerUid);
        devDoc.put("deviceAuthUid", authUid);
        devDoc.put("installationId", installId);
        devDoc.put("bleSharedSecret", bleSecret);

        CommandExecutionManager.PairingRecoveryResult okResult =
                CommandExecutionManager.evaluateRecovery(authUid, installId, invs, devDoc);
        assertTrue("One valid approved invitation must succeed", okResult.success);
        assertEquals(ownerUid, okResult.ownerUid);
        assertEquals(bleSecret, okResult.bleSharedSecret);

        // Case 2: Several duplicate invitations for the same verified owner and target
        List<Map<String, Object>> duplicateInvs = new ArrayList<>(invs);
        Map<String, Object> dupInv = new HashMap<>(inv);
        duplicateInvs.add(dupInv);
        CommandExecutionManager.PairingRecoveryResult dupResult =
                CommandExecutionManager.evaluateRecovery(authUid, installId, duplicateInvs, devDoc);
        assertTrue("Several duplicate invitations for the same verified owner and target must resolve successfully", dupResult.success);
        assertEquals(ownerUid, dupResult.ownerUid);

        // Case 3: Multiple conflicting owners
        List<Map<String, Object>> conflictingInvs = new ArrayList<>(invs);
        Map<String, Object> conflictInv = new HashMap<>(inv);
        conflictInv.put("ownerUid", "different-owner-888");
        conflictingInvs.add(conflictInv);

        Map<String, Map<String, Object>> multiOwnerDocs = new HashMap<>();
        multiOwnerDocs.put(ownerUid, devDoc);
        Map<String, Object> conflictDevDoc = new HashMap<>(devDoc);
        conflictDevDoc.put("ownerUid", "different-owner-888");
        multiOwnerDocs.put("different-owner-888", conflictDevDoc);

        CommandExecutionManager.PairingRecoveryResult conflictResult =
                CommandExecutionManager.evaluateRecoveryMultiOwner(authUid, installId, conflictingInvs, multiOwnerDocs, null);
        assertFalse("Multiple conflicting owners must be rejected", conflictResult.success);
        assertTrue("Must report explicit ownership conflict", conflictResult.rejectionReason.contains("Ownership conflict"));

        // Case 4: Missing corresponding device document
        CommandExecutionManager.PairingRecoveryResult missingDoc =
                CommandExecutionManager.evaluateRecovery(authUid, installId, invs, null);
        assertFalse("Missing device doc must be rejected", missingDoc.success);
        assertTrue(missingDoc.rejectionReason.contains("does not exist"));

        // Case 5: Revoked target device
        Map<String, Object> revokedDoc = new HashMap<>(devDoc);
        revokedDoc.put("status", "REVOKED");
        CommandExecutionManager.PairingRecoveryResult revResult =
                CommandExecutionManager.evaluateRecovery(authUid, installId, invs, revokedDoc);
        assertFalse("Revoked target device must be rejected", revResult.success);
        assertTrue(revResult.rejectionReason.contains("REVOKED"));

        // Case 6: Incorrect installation ID
        CommandExecutionManager.PairingRecoveryResult instMismatch =
                CommandExecutionManager.evaluateRecovery(authUid, "different-inst", invs, devDoc);
        assertFalse("Installation ID mismatch must be rejected", instMismatch.success);
        assertTrue(instMismatch.rejectionReason.contains("Installation ID mismatch"));

        // Case 7: Incorrect claiming UID
        CommandExecutionManager.PairingRecoveryResult uidMismatch =
                CommandExecutionManager.evaluateRecovery("different-uid", installId, invs, devDoc);
        assertFalse("Claiming UID mismatch must be rejected", uidMismatch.success);
        assertTrue(uidMismatch.rejectionReason.contains("Authenticated UID mismatch"));

        // Case 8: Missing shared secret when required
        CommandExecutionManager.PairingRecoveryResult secMissing =
                CommandExecutionManager.evaluateRecovery(authUid, installId, invs, devDoc, "different-required-secret");
        assertFalse("Secret mismatch with required session secret must be rejected", secMissing.success);
        assertTrue(secMissing.rejectionReason.contains("shared secret mismatch"));

        // Case 9: Secret mismatch between invitation and device doc
        Map<String, Object> secretMismatchDevDoc = new HashMap<>(devDoc);
        secretMismatchDevDoc.put("bleSharedSecret", "inconsistent-secret-xyz");
        CommandExecutionManager.PairingRecoveryResult secMismatchResult =
                CommandExecutionManager.evaluateRecovery(authUid, installId, invs, secretMismatchDevDoc);
        assertFalse("Secret mismatch between invitation and device document must be rejected", secMismatchResult.success);
        assertTrue(secMismatchResult.rejectionReason.contains("Pairing credentials mismatch"));

        // Case 10: Expired or rejected invitation
        List<Map<String, Object>> rejectedInvs = new ArrayList<>();
        Map<String, Object> rejInv = new HashMap<>(inv);
        rejInv.put("status", "REJECTED");
        rejectedInvs.add(rejInv);
        CommandExecutionManager.PairingRecoveryResult rejResult =
                CommandExecutionManager.evaluateRecovery(authUid, installId, rejectedInvs, devDoc);
        assertFalse("REJECTED status invitation must be rejected", rejResult.success);
        assertTrue(rejResult.rejectionReason.contains("not APPROVED"));

        // Case 11: Incorrect owner/target direction (ownerUid == authUid)
        List<Map<String, Object>> selfInvs = new ArrayList<>();
        Map<String, Object> selfInv = new HashMap<>(inv);
        selfInv.put("ownerUid", authUid); // owner cannot be claiming device itself
        selfInvs.add(selfInv);
        CommandExecutionManager.PairingRecoveryResult selfResult =
                CommandExecutionManager.evaluateRecovery(authUid, installId, selfInvs, devDoc);
        assertFalse("Owner claiming itself as secondary device must be rejected", selfResult.success);
        assertTrue(selfResult.rejectionReason.contains("owner/target direction"));

        // Case 12: Unauthorized recovery (empty UID or empty installation ID)
        CommandExecutionManager.PairingRecoveryResult emptyAuth =
                CommandExecutionManager.evaluateRecovery("", installId, invs, devDoc);
        assertFalse("Empty auth UID must be rejected", emptyAuth.success);

        CommandExecutionManager.PairingRecoveryResult emptyInst =
                CommandExecutionManager.evaluateRecovery(authUid, "", invs, devDoc);
        assertFalse("Empty installation ID must be rejected", emptyInst.success);
    }

    // =========================================================================
    // 11. LISTENER STARTUP IS BLOCKED UNTIL SECONDARY PAIRING IS VERIFIED
    // =========================================================================

    @Test
    public void testListenerStartupIsBlockedWhenSecondaryPairingIsRejected() {
        // Mock session with anonymous/secondary auth user
        sessionManager.setInstallationId("inst-samsung-001");
        sessionManager.setPairedSecondaryDevice(false);
        sessionManager.saveFirebaseAuthSession("secondary-auth-uid-123", "sec@test.com", "Secondary User");

        // Configure recovery to be REJECTED (e.g. device is revoked)
        fakeRecovery.mode = FakePairingRecoveryService.Mode.REJECTED;
        fakeRecovery.rejectionReason = "Device is REVOKED under owner";

        // Invoke startListeningToCommands
        manager.startListeningToCommands();

        // Verify listener was NOT started under secondary UID
        assertFalse("Device must NOT be marked as paired secondary when recovery is rejected",
                sessionManager.isPairedSecondaryDevice());
        assertNull("Paired owner UID must not be set on rejected recovery",
                sessionManager.getPairedOwnerUid());
    }

    @Test
    public void testListenerStartupProceedsWhenSecondaryPairingIsVerified() {
        sessionManager.setInstallationId("inst-samsung-001");
        sessionManager.setPairedSecondaryDevice(false);
        sessionManager.saveFirebaseAuthSession("secondary-auth-uid-123", "sec@test.com", "Secondary User");

        fakeRecovery.mode = FakePairingRecoveryService.Mode.SUCCESS;
        fakeRecovery.ownerUid = "verified-owner-789";
        fakeRecovery.deviceName = "Samsung Galaxy A07";
        fakeRecovery.bleSecret = "ble-secret-xyz";

        manager.startListeningToCommands();

        assertTrue("Device must be marked as paired secondary when recovery succeeds",
                sessionManager.isPairedSecondaryDevice());
        assertEquals("verified-owner-789", sessionManager.getPairedOwnerUid());
        assertEquals("Samsung Galaxy A07", sessionManager.getDeviceName());
        assertEquals("ble-secret-xyz", sessionManager.getBleSharedSecret());
    }

    // =========================================================================
    // DETERMINISTIC FAKES IMPLEMENTING ACTUAL INTERFACES
    // =========================================================================

    static class FakeLocalPersistence implements ILocalPersistence {
        boolean shouldFailLocationInsert = false;
        final AtomicInteger locationInsertCount = new AtomicInteger(0);
        final AtomicInteger locationSyncMarkCount = new AtomicInteger(0);

        private long idGenerator = 1L;
        private final Map<Long, LocationEntity> locationsById = new HashMap<>();
        private final Map<String, LocationEntity> locationsByClientUuid = new HashMap<>();
        private final Map<String, CommandEntity> commandsById = new HashMap<>();

        @Override
        public synchronized LocationEntity getLocationByClientUuid(String clientUuid) {
            return locationsByClientUuid.get(clientUuid);
        }

        @Override
        public synchronized long insertLocation(LocationEntity entity) {
            locationInsertCount.incrementAndGet();
            if (shouldFailLocationInsert) {
                return -1;
            }
            long id = idGenerator++;
            entity.setId(id);
            locationsById.put(id, entity);
            locationsByClientUuid.put(entity.getClientUuid(), entity);
            return id;
        }

        @Override
        public synchronized void markLocationSynced(long id) {
            locationSyncMarkCount.incrementAndGet();
            LocationEntity entity = locationsById.get(id);
            if (entity != null) {
                entity.setSynced(true);
            }
        }

        @Override
        public synchronized CommandEntity getCommandById(String commandId) {
            return commandsById.get(commandId);
        }

        @Override
        public synchronized void insertOrUpdateCommand(CommandEntity command) {
            commandsById.put(command.getCommandId(), command);
        }

        @Override
        public synchronized void updateCommandExecution(String commandId, String status, String result, long executedAt, String syncStatus) {
            CommandEntity existing = commandsById.get(commandId);
            if (existing != null) {
                existing.setStatus(status);
                existing.setResult(result);
                existing.setExecutedAt(executedAt);
                if (syncStatus != null) {
                    existing.setSyncStatus(syncStatus);
                }
            } else {
                CommandEntity created = new CommandEntity(
                        commandId, "", "", "", "",
                        status, result, "CLOUD", executedAt, 0, 0, executedAt,
                        syncStatus != null ? syncStatus : "PENDING", 0
                );
                commandsById.put(commandId, created);
            }
        }

        @Override
        public synchronized void updateCommandSyncStatus(String commandId, String syncStatus) {
            CommandEntity cmd = commandsById.get(commandId);
            if (cmd != null) {
                cmd.setSyncStatus(syncStatus);
            }
        }

        @Override
        public synchronized List<CommandEntity> getPendingSyncCommands() {
            List<CommandEntity> list = new ArrayList<>();
            for (CommandEntity cmd : commandsById.values()) {
                if ("PENDING".equalsIgnoreCase(cmd.getSyncStatus())) {
                    list.add(cmd);
                }
            }
            return list;
        }

        public synchronized List<LocationEntity> getAllLocations() {
            return new ArrayList<>(locationsById.values());
        }
    }

    static class FakeCloudCommandBatchPersister implements ICloudCommandBatchPersister {
        boolean shouldFailBatch = false;
        boolean shouldFailCommandStatusUpdate = false;
        final AtomicInteger batchCommitCount = new AtomicInteger(0);
        final AtomicInteger statusUpdateCount = new AtomicInteger(0);

        Map<String, Object> lastLocationMap;
        Map<String, Object> lastDeviceUpdates;
        Map<String, Object> lastCommandUpdates;

        @Override
        public void commitLocateBatch(String uid, String deviceId, String commandId, String requestId,
                                      Map<String, Object> locationMap, Map<String, Object> deviceUpdates,
                                      Map<String, Object> commandUpdates, Callback callback) {
            batchCommitCount.incrementAndGet();
            this.lastLocationMap = locationMap;
            this.lastDeviceUpdates = deviceUpdates;
            this.lastCommandUpdates = commandUpdates;

            if (shouldFailBatch) {
                if (callback != null) callback.onFailure(new RuntimeException("Simulated Firestore batch failure"));
            } else {
                if (callback != null) callback.onSuccess();
            }
        }

        @Override
        public void updateCommandStatus(String uid, String deviceId, String commandId,
                                        Map<String, Object> updates, Callback callback) {
            statusUpdateCount.incrementAndGet();
            if (shouldFailCommandStatusUpdate) {
                if (callback != null) callback.onFailure(new RuntimeException("Simulated Firestore update failure"));
            } else {
                if (callback != null) callback.onSuccess();
            }
        }
    }

    static class DeferredCloudBatchPersister implements ICloudCommandBatchPersister {
        private Callback pendingCallback;

        @Override
        public void commitLocateBatch(String uid, String deviceId, String commandId, String requestId,
                                      Map<String, Object> locationMap, Map<String, Object> deviceUpdates,
                                      Map<String, Object> commandUpdates, Callback callback) {
            this.pendingCallback = callback;
        }

        @Override
        public void updateCommandStatus(String uid, String deviceId, String commandId,
                                        Map<String, Object> updates, Callback callback) {
            if (callback != null) callback.onSuccess();
        }

        public void triggerSuccess() {
            if (pendingCallback != null) {
                pendingCallback.onSuccess();
            }
        }
    }

    static class FakePairingRecoveryService implements IPairingRecoveryService {
        enum Mode { SUCCESS, REJECTED, ERROR, NO_INVITATION }

        Mode mode = Mode.NO_INVITATION;
        String ownerUid;
        String deviceName;
        String bleSecret;
        String rejectionReason;
        Exception errorException = new RuntimeException("Simulated recovery network failure");

        @Override
        public void checkRecovery(String authUid, String installationId, RecoveryCallback callback) {
            if (callback == null) return;
            switch (mode) {
                case SUCCESS:
                    callback.onRecoverySuccess(ownerUid, deviceName, bleSecret);
                    break;
                case REJECTED:
                    callback.onRecoveryRejected(rejectionReason);
                    break;
                case ERROR:
                    callback.onRecoveryError(errorException);
                    break;
                case NO_INVITATION:
                    callback.onNoSecondaryInvitation();
                    break;
            }
        }
    }

    static class DirectExecutor extends AbstractExecutorService {
        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() { return new ArrayList<>(); }

        @Override
        public boolean isShutdown() { return false; }

        @Override
        public boolean isTerminated() { return false; }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    static class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = values.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object v = values.get(key);
            return v instanceof Set ? (Set<String>) v : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object v = values.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = values.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object v = values.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = values.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new InMemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        class InMemoryEditor implements Editor {
            private final Map<String, Object> temp = new HashMap<>();
            private final Set<String> removed = new HashSet<>();
            private boolean clear = false;

            @Override
            public Editor putString(String key, String value) {
                temp.put(key, value);
                removed.remove(key);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                temp.put(key, values);
                removed.remove(key);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                temp.put(key, value);
                removed.remove(key);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                temp.put(key, value);
                removed.remove(key);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                temp.put(key, value);
                removed.remove(key);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                temp.put(key, value);
                removed.remove(key);
                return this;
            }

            @Override
            public Editor remove(String key) {
                removed.add(key);
                temp.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) {
                    values.clear();
                }
                for (String r : removed) {
                    values.remove(r);
                }
                values.putAll(temp);
            }
        }
    }
}
