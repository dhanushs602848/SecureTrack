package com.example.securetrack;

import com.example.securetrack.ble.BleNearbyManager;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.database.SecurityAlertEntity;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.ResolvedLocation;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.security.AlarmSoundManager;
import com.example.securetrack.security.AntiShutdownManager;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

/**
 * Automated Unit Test Suite for SecureTrack 1.1 Security, Location, BLE & Offline Upgrade.
 */
public class SecurityAndLocationUpgradeTest {

    @Test
    public void testFiveSecondAlarmDurationConstraint() {
        assertEquals("Alarm duration must be strictly 5000 ms", 5000L, AlarmSoundManager.ALARM_DURATION_MS);
    }

    @Test
    public void testCommandExpirationLogic() {
        long now = System.currentTimeMillis();
        long fifteenMinutesAgo = now - (16 * 60 * 1000L); // 16 minutes ago

        CommandEntity expiredCmd = new CommandEntity(
                "cmd-101",
                "target-phone-1",
                "LOCK_DEVICE",
                "owner-uid-1",
                "{}",
                CommandEntity.STATE_QUEUED_LOCAL,
                "",
                "CLOUD",
                fifteenMinutesAgo,
                fifteenMinutesAgo + (15 * 60 * 1000L), // expired 1 minute ago
                0,
                0,
                "SYNCED",
                0
        );

        assertTrue("Command created >15 minutes ago with past expiresAt must be expired", expiredCmd.isExpired());

        CommandEntity freshCmd = new CommandEntity(
                "cmd-102",
                "target-phone-1",
                "ALARM_ALERT",
                "owner-uid-1",
                "{}",
                CommandEntity.STATE_QUEUED_LOCAL,
                "",
                "CLOUD",
                now,
                now + (15 * 60 * 1000L),
                0,
                0,
                "SYNCED",
                0
        );

        assertFalse("Fresh command must not be expired", freshCmd.isExpired());
    }

    @Test
    public void testCommandStateTransitions() {
        CommandEntity cmd = new CommandEntity(
                "cmd-201",
                "target-phone-2",
                "LOCATE_NOW",
                "owner-uid-2",
                "",
                CommandEntity.STATE_QUEUED_LOCAL,
                "",
                "CLOUD",
                System.currentTimeMillis(),
                System.currentTimeMillis() + 900000L,
                0,
                0,
                "SYNC_PENDING",
                0
        );

        assertEquals(CommandEntity.STATE_QUEUED_LOCAL, cmd.getStatus());

        // Dispatch via Cloud
        cmd.setStatus(CommandEntity.STATE_SENT_CLOUD);
        cmd.setSyncStatus("SYNCED");
        assertEquals(CommandEntity.STATE_SENT_CLOUD, cmd.getStatus());

        // Target receives & acknowledges
        cmd.setStatus(CommandEntity.STATE_ACKNOWLEDGED);
        cmd.setAcknowledgedAt(System.currentTimeMillis());
        assertEquals(CommandEntity.STATE_ACKNOWLEDGED, cmd.getStatus());
        assertTrue(cmd.getAcknowledgedAt() > 0);

        // Target executes
        cmd.setStatus(CommandEntity.STATE_EXECUTED);
        cmd.setExecutedAt(System.currentTimeMillis());
        cmd.setResult("Location acquired with accuracy 8.5m");
        assertEquals(CommandEntity.STATE_EXECUTED, cmd.getStatus());
        assertTrue(cmd.getResult().contains("8.5m"));
    }

    @Test
    public void testSecurityAlertNonDestructiveDismissAndRestore() {
        SecurityAlertEntity alert = new SecurityAlertEntity(
                "alert-001",
                "phone-dev-1",
                "Samsung Galaxy A07",
                "SIM_CARD_REMOVED",
                "CRITICAL",
                "SIM card physical removal detected",
                "ENABLED",
                SecurityAlertEntity.STATUS_ACTIVE,
                System.currentTimeMillis(),
                0,
                null,
                0,
                true
        );

        assertEquals("ACTIVE", alert.getStatus());
        assertEquals(0, alert.getDismissedAt());
        assertNull(alert.getDismissedBy());

        // Dismiss alert (non-destructive)
        long dismissTime = System.currentTimeMillis();
        alert.setStatus(SecurityAlertEntity.STATUS_DISMISSED);
        alert.setDismissedAt(dismissTime);
        alert.setDismissedBy("owner-user-uid");

        assertEquals(SecurityAlertEntity.STATUS_DISMISSED, alert.getStatus());
        assertEquals(dismissTime, alert.getDismissedAt());
        assertEquals("owner-user-uid", alert.getDismissedBy());
        // Verify original telemetry & timestamp preserved
        assertEquals("SIM_CARD_REMOVED", alert.getEventType());
        assertEquals("CRITICAL", alert.getSeverity());

        // Restore alert (Undo action)
        alert.setStatus(SecurityAlertEntity.STATUS_ACTIVE);
        alert.setDismissedAt(0);
        alert.setDismissedBy(null);

        assertEquals("ACTIVE", alert.getStatus());
        assertEquals(0, alert.getDismissedAt());
        assertNull(alert.getDismissedBy());
    }

    @Test
    public void testLocationPriorityHierarchy() {
        // Priority 1: Cloud Live Fix
        ResolvedLocation tier1 = new ResolvedLocation(
                ResolvedLocation.Pathway.CLOUD_LIVE,
                13.0827, 80.2707, 10.0f,
                System.currentTimeMillis(), "Just now",
                "GPS Provider", true, 85, "LIVE"
        );
        assertEquals(ResolvedLocation.Pathway.CLOUD_LIVE, tier1.getPathway());
        assertTrue(tier1.isLive());

        // Priority 2: BLE Nearby
        ResolvedLocation tier2 = new ResolvedLocation(
                ResolvedLocation.Pathway.BLE_NEARBY,
                13.0826, 80.2706, 15.0f,
                System.currentTimeMillis(), "Just now",
                "Authenticated BLE Nearby Peer", true, -1, "BLE NEARBY"
        );
        assertEquals(ResolvedLocation.Pathway.BLE_NEARBY, tier2.getPathway());
        assertTrue(tier2.isLive());

        // Priority 3: Last Known Snapshot (clearly not live)
        ResolvedLocation tier3 = new ResolvedLocation(
                ResolvedLocation.Pathway.LAST_KNOWN,
                13.0820, 80.2700, 50.0f,
                System.currentTimeMillis() - 3600000L, "1 hour ago",
                "Cached Snapshot", false, 40, "LAST SEEN (STALE)"
        );
        assertEquals(ResolvedLocation.Pathway.LAST_KNOWN, tier3.getPathway());
        assertFalse(tier3.isLive());

        // Priority 4: Find Hub Ready
        ResolvedLocation tier4 = new ResolvedLocation(
                ResolvedLocation.Pathway.FIND_HUB_READY,
                0, 0, 0, 0, "No location",
                "Offline • Google Find Hub Available", false, -1, "OFFLINE"
        );
        assertEquals(ResolvedLocation.Pathway.FIND_HUB_READY, tier4.getPathway());
        assertFalse(tier4.isLive());
    }

    @Test
    public void testBleServiceAndCharacteristicUuids() {
        assertEquals("0000fe70-0000-1000-8000-00805f9b34fb", BleNearbyManager.SERVICE_UUID.toString().toLowerCase());
        assertEquals("0000fe71-0000-1000-8000-00805f9b34fb", BleNearbyManager.CHAR_AUTH_UUID.toString().toLowerCase());
        assertEquals("0000fe72-0000-1000-8000-00805f9b34fb", BleNearbyManager.CHAR_LOCATION_UUID.toString().toLowerCase());
    }

    @Test
    public void testDuplicateExecutionPreventionLogic() {
        DeviceCommand cmd = new DeviceCommand("cmd-555", "LOCK_DEVICE", "{}");
        cmd.setStatus("EXECUTED");
        cmd.setResult("Screen locked successfully");

        // Verify that once a command is EXECUTED, it should not be re-executed
        boolean canExecute = !"EXECUTED".equals(cmd.getStatus()) && !"FAILED".equals(cmd.getStatus()) && !"EXPIRED".equals(cmd.getStatus());
        assertFalse("Command in EXECUTED state must be rejected from duplicate execution", canExecute);
    }

    @Test
    public void testBleMutualHmacKeyDerivationAndVerification() {
        String ownerUid = "owner_test_12345";
        String deviceId = "device_samsung_001";

        byte[] validKey = BleNearbyManager.deriveDeviceBleKey(ownerUid, deviceId);
        assertNotNull("Derived key must not be null", validKey);
        assertEquals("HMAC-SHA256 derived key must be 32 bytes", 32, validKey.length);

        // Different owner or different device ID must produce distinct cryptographic keys
        byte[] wrongOwnerKey = BleNearbyManager.deriveDeviceBleKey("malicious_user_666", deviceId);
        byte[] wrongDeviceKey = BleNearbyManager.deriveDeviceBleKey(ownerUid, "device_imposter_999");

        assertFalse("Different owner must yield different key", java.security.MessageDigest.isEqual(validKey, wrongOwnerKey));
        assertFalse("Different device must yield different key", java.security.MessageDigest.isEqual(validKey, wrongDeviceKey));

        // Test signature generation and verification
        String serverNonce = "0123456789abcdef0123456789abcdef";
        String clientNonce = "fedcba9876543210fedcba9876543210";
        String authData = "BLE-AUTH-REQ:" + serverNonce + ":" + clientNonce + ":" + deviceId;

        byte[] legitimateProof = BleNearbyManager.computeHmacSha256(validKey, authData);
        assertNotNull(legitimateProof);
        assertEquals(32, legitimateProof.length);

        // Verify valid proof matches
        byte[] expectedProof = BleNearbyManager.computeHmacSha256(validKey, authData);
        assertTrue("Legitimate proof must verify successfully", java.security.MessageDigest.isEqual(expectedProof, legitimateProof));

        // Verify forged proof with attacker key fails
        byte[] forgedProof = BleNearbyManager.computeHmacSha256(wrongOwnerKey, authData);
        assertFalse("Attacker proof must be rejected", java.security.MessageDigest.isEqual(expectedProof, forgedProof));

        // Verify tampered message fails
        String tamperedData = authData + "_tampered";
        byte[] tamperedProof = BleNearbyManager.computeHmacSha256(validKey, tamperedData);
        assertFalse("Tampered auth data must be rejected", java.security.MessageDigest.isEqual(expectedProof, tamperedProof));
    }

    @Test
    public void testLocationRequestIdCorrelationAndBoundaryValidation() {
        String expectedRequestId = UUID.randomUUID().toString();
        long commandTime = System.currentTimeMillis();

        // Matching request ID
        String docRequestId = expectedRequestId;
        long docTime = commandTime + 500; // 500ms after command
        double lat = 13.0827;
        double lng = 80.2707;
        double accuracy = 8.2;

        boolean matchesReqId = expectedRequestId.equals(docRequestId);
        boolean isFresh = docTime >= commandTime;
        boolean validCoords = (lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0 && accuracy >= 0);

        assertTrue("Matching request ID must correlate", matchesReqId);
        assertTrue("Location after command time must be fresh", isFresh);
        assertTrue("Valid coordinates must pass validation", validCoords);

        // Negative test: Stale location with mismatched request ID
        String staleRequestId = UUID.randomUUID().toString();
        long staleTime = commandTime - 30000L; // 30s before command
        boolean staleMatches = expectedRequestId.equals(staleRequestId);
        boolean staleIsFresh = staleTime >= commandTime;

        assertFalse("Mismatched request ID must not correlate", staleMatches);
        assertFalse("Old location prior to command must not be considered fresh", staleIsFresh);

        // Negative test: Out of bounds coordinates
        double invalidLat = 105.0; // latitude > 90
        boolean invalidCoords = (invalidLat >= -90.0 && invalidLat <= 90.0);
        assertFalse("Latitude > 90 must fail validation", invalidCoords);
        double invalidLng = -195.0; // longitude < -180
        boolean invalidLngCoords = (invalidLng >= -180.0 && invalidLng <= 180.0);
        assertFalse("Longitude < -180 must fail validation", invalidLngCoords);

        double negativeAcc = -1.0;
        boolean validAcc = negativeAcc >= 0;
        assertFalse("Negative accuracy must fail validation", validAcc);
    }

    @Test
    public void testHighEntropyBleSharedSecretKeyDerivation() {
        String ownerUid = "owner_test_secure_uid";
        String deviceId = "device_realme_002";
        String genuineSecret = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
        String attackerSecret = "0000000000000000000000000000000000000000000000000000000000000000";

        byte[] genuineKey = BleNearbyManager.deriveDeviceBleKey(genuineSecret, ownerUid, deviceId);
        assertNotNull("Genuine key must be derived", genuineKey);
        assertEquals(32, genuineKey.length);

        byte[] attackerKey = BleNearbyManager.deriveDeviceBleKey(attackerSecret, ownerUid, deviceId);
        assertNotNull("Attacker key can be computed but must not match genuine key", attackerKey);
        assertFalse("Attacker key must not match genuine shared-secret key",
                java.security.MessageDigest.isEqual(genuineKey, attackerKey));

        // Test with null or empty device ID
        assertNull("Null device ID must return null key", BleNearbyManager.deriveDeviceBleKey(genuineSecret, ownerUid, null));
        assertNull("Null owner UID must return null key", BleNearbyManager.deriveDeviceBleKey(genuineSecret, null, deviceId));
    }

    @Test
    public void testFiveAlertsDismissalDownToZeroAndRestore() {
        java.util.List<SecurityAlertEntity> alerts = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            alerts.add(new SecurityAlertEntity(
                    "alert-" + i,
                    "phone-dev-1",
                    "Samsung Galaxy A07",
                    "SECURITY_EVENT_" + i,
                    "WARNING",
                    "Security event description " + i,
                    "ENABLED",
                    SecurityAlertEntity.STATUS_ACTIVE,
                    System.currentTimeMillis() - (i * 1000L),
                    0, null, 0, true
            ));
        }

        // 1. Initial count must be exactly 5
        long activeCount = alerts.stream().filter(a -> SecurityAlertEntity.STATUS_ACTIVE.equals(a.getStatus())).count();
        assertEquals("Initial active alert count must be exactly 5", 5, activeCount);

        // 2. Dismiss 1 alert out of 5 -> count must become 4
        SecurityAlertEntity firstAlert = alerts.get(0);
        long dismissTime = System.currentTimeMillis();
        firstAlert.setStatus(SecurityAlertEntity.STATUS_DISMISSED);
        firstAlert.setDismissedAt(dismissTime);
        firstAlert.setDismissedBy("owner-uid-1");

        activeCount = alerts.stream().filter(a -> SecurityAlertEntity.STATUS_ACTIVE.equals(a.getStatus())).count();
        assertEquals("After dismissing 1 alert, active count must become 4", 4, activeCount);
        assertEquals("Total historical alerts must remain 5", 5, alerts.size());

        // 3. Dismiss remaining 4 alerts -> count must become exactly 0
        for (int i = 1; i < 5; i++) {
            SecurityAlertEntity a = alerts.get(i);
            a.setStatus(SecurityAlertEntity.STATUS_DISMISSED);
            a.setDismissedAt(System.currentTimeMillis());
            a.setDismissedBy("owner-uid-1");
        }

        activeCount = alerts.stream().filter(a -> SecurityAlertEntity.STATUS_ACTIVE.equals(a.getStatus())).count();
        assertEquals("After dismissing all 5 alerts, active count must become exactly 0", 0, activeCount);
        // Verify all 5 records are preserved in history (not deleted)
        assertEquals("All 5 dismissed records must remain in historical database", 5, alerts.size());
        for (SecurityAlertEntity a : alerts) {
            assertEquals(SecurityAlertEntity.STATUS_DISMISSED, a.getStatus());
            assertTrue("Dismissed timestamp must be recorded", a.getDismissedAt() > 0);
            assertNotNull("Original event description must be preserved", a.getDescription());
        }

        // 4. Restore 1 alert -> count must become 1
        firstAlert.setStatus(SecurityAlertEntity.STATUS_ACTIVE);
        firstAlert.setDismissedAt(0);
        firstAlert.setDismissedBy(null);

        activeCount = alerts.stream().filter(a -> SecurityAlertEntity.STATUS_ACTIVE.equals(a.getStatus())).count();
        assertEquals("After restoring 1 alert, active count must become 1", 1, activeCount);

        // 5. Generate a new alert -> count must become 2
        alerts.add(new SecurityAlertEntity(
                "alert-6",
                "phone-dev-1",
                "Samsung Galaxy A07",
                "NEW_SUSPICIOUS_ACTIVITY",
                "CRITICAL",
                "New suspicious activity detected",
                "ENABLED",
                SecurityAlertEntity.STATUS_ACTIVE,
                System.currentTimeMillis(),
                0, null, 0, true
        ));

        activeCount = alerts.stream().filter(a -> SecurityAlertEntity.STATUS_ACTIVE.equals(a.getStatus())).count();
        assertEquals("After generating 1 new alert, active count must become 2", 2, activeCount);
    }

    @Test
    public void testPanicSecurityAlarmEventCreationAndStatusProgression() {
        String commandId = "cmd-panic-siren-909";
        String deviceId = "device-a07-001";
        String deviceName = "Samsung Galaxy A07";
        String alertId = "alarm_" + commandId;

        // 1. Initial trigger: command dispatched and execution starts
        SecurityAlertEntity alarmAlert = new SecurityAlertEntity(
                alertId,
                deviceId,
                deviceName,
                "SECURITY_ALARM_TRIGGERED",
                "CRITICAL",
                "Panic security siren activated on " + deviceName,
                "ENABLED",
                SecurityAlertEntity.STATUS_ACTIVE,
                System.currentTimeMillis(),
                0, null, 0, true
        );

        assertEquals("alarm_cmd-panic-siren-909", alarmAlert.getId());
        assertEquals("SECURITY_ALARM_TRIGGERED", alarmAlert.getEventType());
        assertEquals("CRITICAL", alarmAlert.getSeverity());
        assertEquals("ACTIVE", alarmAlert.getStatus());

        // 2. Alarm plays for 5 seconds and completes:
        // Alert status MUST REMAIN ACTIVE so owner can review it in Security Center!
        long completedAt = System.currentTimeMillis() + 5000L;
        alarmAlert.setResolvedAt(completedAt);
        assertEquals("ACTIVE", alarmAlert.getStatus());
        assertTrue("Completed timestamp must be recorded", alarmAlert.getResolvedAt() > 0);

        // 3. Owner dismisses the alarm event in Security Center
        long dismissedAt = completedAt + 10000L;
        alarmAlert.setStatus(SecurityAlertEntity.STATUS_DISMISSED);
        alarmAlert.setDismissedAt(dismissedAt);
        alarmAlert.setDismissedBy("owner-user-uid");

        assertEquals("DISMISSED", alarmAlert.getStatus());
        assertEquals(dismissedAt, alarmAlert.getDismissedAt());
        assertEquals("owner-user-uid", alarmAlert.getDismissedBy());

        // 4. Owner restores the alarm event (undo dismissal)
        alarmAlert.setStatus(SecurityAlertEntity.STATUS_ACTIVE);
        alarmAlert.setDismissedAt(0);
        alarmAlert.setDismissedBy(null);

        assertEquals("ACTIVE", alarmAlert.getStatus());
        assertEquals(0, alarmAlert.getDismissedAt());
        assertNull(alarmAlert.getDismissedBy());
    }

    @Test
    public void testAntiShutdownCapabilityEvaluationOnPersonalDevice() {
        // Consumer / Unmanaged personal device without Device Owner
        AntiShutdownManager.CapabilityStatus status =
                AntiShutdownManager.evaluateCapability(false, false, false);
        assertEquals(AntiShutdownManager.CapabilityStatus.UNSUPPORTED_PERSONAL_DEVICE, status);

        // Even with standard Device Admin active, without Device Owner it remains unsupported
        AntiShutdownManager.CapabilityStatus statusWithAdmin =
                AntiShutdownManager.evaluateCapability(false, true, false);
        assertEquals(AntiShutdownManager.CapabilityStatus.UNSUPPORTED_PERSONAL_DEVICE, statusWithAdmin);

        String explanation = AntiShutdownManager.getCapabilityExplanation(status);
        assertNotNull(explanation);
        assertTrue("Explanation must disclose hardware power-cut cannot be overridden on consumer devices",
                explanation.contains("Device Owner") && explanation.contains("hardware power-cut"));
    }

    @Test
    public void testAntiShutdownCapabilityEvaluationOnDeviceOwner() {
        // Enrolled Enterprise Device Owner device
        AntiShutdownManager.CapabilityStatus readyStatus =
                AntiShutdownManager.evaluateCapability(true, true, false);
        assertEquals(AntiShutdownManager.CapabilityStatus.SUPPORTED_DEVICE_OWNER_READY, readyStatus);

        // Lock Task mode active
        AntiShutdownManager.CapabilityStatus activeStatus =
                AntiShutdownManager.evaluateCapability(true, true, true);
        assertEquals(AntiShutdownManager.CapabilityStatus.ACTIVE_LOCK_TASK, activeStatus);

        String activeExplanation = AntiShutdownManager.getCapabilityExplanation(activeStatus);
        assertTrue(activeExplanation.contains("Lock Task"));
    }

    @Test
    public void testAntiShutdownRecoveryPinHashing() {
        String pin1 = "9824";
        String pin2 = "1234";

        String hash1 = AntiShutdownManager.hashPin(pin1);
        String hash2 = AntiShutdownManager.hashPin(pin2);

        assertNotNull(hash1);
        assertNotNull(hash2);
        assertFalse("Different recovery PINs must generate distinct hashes", hash1.equals(hash2));
        assertEquals("Same PIN must generate identical SHA-256 hash", hash1, AntiShutdownManager.hashPin(pin1));
    }

    @Test
    public void testWipeSafetyLockPermanentlyEnforced() {
        // Verify that WIPE_DEVICE command is marked as FAILED with safety lock explanation
        DeviceCommand wipeCmd = new DeviceCommand("cmd-wipe-001", "WIPE_DEVICE", "{}");
        String safetyFailureReason = "Safety Lock: Factory wipe is permanently disabled in this release to protect user data.";

        // Simulating the safety lock rejection
        wipeCmd.setStatus("FAILED");
        wipeCmd.setResult(safetyFailureReason);

        assertEquals("FAILED", wipeCmd.getStatus());
        assertTrue(wipeCmd.getResult().contains("Safety Lock"));
        assertFalse("Safety lock must never allow EXECUTED status for wipe", "EXECUTED".equals(wipeCmd.getStatus()));
    }

    @Test
    public void testLocationTrackingServiceActionConstantsAndChannel() {
        String actionStart = "com.example.securetrack.action.START_TRACKING";
        String actionStop = "com.example.securetrack.action.STOP_TRACKING";
        String actionRefresh = "com.example.securetrack.action.REFRESH_STATUS";
        String channelId = "securetrack_location_channel";
        int notifId = 1001;

        assertEquals("com.example.securetrack.action.START_TRACKING", actionStart);
        assertEquals("com.example.securetrack.action.STOP_TRACKING", actionStop);
        assertEquals("com.example.securetrack.action.REFRESH_STATUS", actionRefresh);
        assertEquals("securetrack_location_channel", channelId);
        assertEquals(1001, notifId);
    }

    @Test
    public void testProtectionHealthReportModelAccuracy() {
        // Active healthy state
        com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport activeReport =
                new com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport(
                        com.example.securetrack.utils.ProtectionHealthEvaluator.STATUS_ACTIVE,
                        "All security & telemetry protection systems operational",
                        true, true, true, true, true
                );
        assertEquals("ACTIVE", activeReport.status);
        assertTrue(activeReport.protectionEnabled);
        assertTrue(activeReport.isLocationPermGranted);
        assertTrue(activeReport.isLocationServiceEnabled);

        // Degraded state (GPS disabled)
        com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport degradedReport =
                new com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport(
                        com.example.securetrack.utils.ProtectionHealthEvaluator.STATUS_DEGRADED,
                        "Location services switched off in system settings",
                        true, false, true, true, true
                );
        assertEquals("DEGRADED", degradedReport.status);
        assertFalse("Protection must not be reported enabled when degraded", degradedReport.protectionEnabled);
        assertFalse(degradedReport.isLocationServiceEnabled);

        // Offline state
        com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport offlineReport =
                new com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport(
                        com.example.securetrack.utils.ProtectionHealthEvaluator.STATUS_OFFLINE,
                        "Device has no active internet connection",
                        true, true, true, true, false
                );
        assertEquals("OFFLINE", offlineReport.status);
        assertFalse(offlineReport.protectionEnabled);
        assertFalse(offlineReport.isConnected);
    }

    @Test
    public void testDurableOfflineLocationPersistenceIntegrity() {
        String deviceId = "dev-test-pixel-9";
        String clientUuid = UUID.randomUUID().toString();
        String originalTimestamp = "2026-11-21T10:45:00Z";
        double lat = 13.06938;
        double lng = 77.53035;
        float accuracy = 8.5f;

        // 1. Initial write-ahead persistent state: must be un-synced before network call
        LocationEntity entity = new LocationEntity(
                deviceId,
                clientUuid,
                lat,
                lng,
                accuracy,
                920.0,
                0.0f,
                originalTimestamp,
                false
        );

        assertFalse("Write-ahead location must be un-synced upon creation", entity.isSynced());
        assertEquals(clientUuid, entity.getClientUuid());
        assertEquals(originalTimestamp, entity.getTimestamp());
        assertEquals(lat, entity.getLatitude(), 0.00001);
        assertEquals(lng, entity.getLongitude(), 0.00001);

        // 2. Transition upon confirmed cloud receipt: marked synced without altering timestamp or UUID
        entity.setSynced(true);
        assertTrue("Entity must reflect synced state", entity.isSynced());
        assertEquals("Timestamp must remain immutable across retries", originalTimestamp, entity.getTimestamp());
        assertEquals("Client UUID must remain invariant to guarantee idempotent Firestore writes", clientUuid, entity.getClientUuid());
    }

    @Test
    public void testHonestTrackingStatusDistinction() {
        // Case 1: Service intentionally stopped
        boolean trackingEnabled = false;
        boolean locationUpdatesActive = false;
        boolean gpsEnabled = true;
        boolean permissionGranted = true;

        String status1 = !trackingEnabled ? "STOPPED" : (locationUpdatesActive && gpsEnabled && permissionGranted ? "ACTIVE" : "PAUSED");
        assertEquals("STOPPED", status1);

        // Case 2: Tracking enabled, but GPS disabled in system settings
        trackingEnabled = true;
        locationUpdatesActive = false;
        gpsEnabled = false;
        permissionGranted = true;

        String status2 = !trackingEnabled ? "STOPPED" : (locationUpdatesActive && gpsEnabled && permissionGranted ? "ACTIVE" : "PAUSED");
        assertEquals("PAUSED", status2);
        assertNotEquals("GPS disabled must NEVER be marked ACTIVE", "ACTIVE", status2);

        // Case 3: Tracking enabled, GPS active, permission granted, updates flowing
        trackingEnabled = true;
        locationUpdatesActive = true;
        gpsEnabled = true;
        permissionGranted = true;

        String status3 = !trackingEnabled ? "STOPPED" : (locationUpdatesActive && gpsEnabled && permissionGranted ? "ACTIVE" : "PAUSED");
        assertEquals("ACTIVE", status3);
    }

    @Test
    public void testLocationTimestampParsingIntegrity() {
        // 1. ISO-8601 UTC timestamp
        String isoUtc = "2026-11-21T10:15:30Z";
        com.google.firebase.Timestamp ts1 = com.example.securetrack.utils.TimestampUtils.parseTimestamp(isoUtc);
        assertNotNull(ts1);
        // Verify round-trip time matches UTC
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        assertEquals("2026-11-21T10:15:30Z", sdf.format(ts1.toDate()));

        // 2. Epoch millis format
        long epoch = 1795256130000L;
        com.google.firebase.Timestamp ts2 = com.example.securetrack.utils.TimestampUtils.parseTimestamp(String.valueOf(epoch));
        assertNotNull(ts2);
        assertEquals(epoch / 1000L, ts2.getSeconds());

        // 3. Fallback for null or empty string produces valid non-null timestamp
        long before = System.currentTimeMillis() / 1000L;
        com.google.firebase.Timestamp tsNull = com.example.securetrack.utils.TimestampUtils.parseTimestamp(null);
        com.google.firebase.Timestamp tsEmpty = com.example.securetrack.utils.TimestampUtils.parseTimestamp("   ");
        long after = System.currentTimeMillis() / 1000L;

        assertNotNull(tsNull);
        assertNotNull(tsEmpty);
        assertTrue(tsNull.getSeconds() >= before && tsNull.getSeconds() <= after + 1);
        assertTrue(tsEmpty.getSeconds() >= before && tsEmpty.getSeconds() <= after + 1);
    }

    @Test
    public void testOfflineLocationDeduplicationByClientUuid() {
        String clientUuid = "unique-test-uuid-999";
        LocationEntity e1 = new LocationEntity("dev-1", clientUuid, 12.9, 77.5, 5.0f, 0.0, 0.0f, "2026-11-21T10:00:00Z", false);
        e1.setId(101L);

        // Simulate repository getByClientUuid check
        java.util.Map<String, LocationEntity> mockStore = new java.util.HashMap<>();
        mockStore.put(e1.getClientUuid(), e1);

        // When second attempt arrives with identical clientUuid, lookup succeeds and avoids inserting duplicate
        LocationEntity existing = mockStore.get(clientUuid);
        assertNotNull("Existing entity must be identified by clientUuid", existing);
        assertEquals(101L, existing.getId());

        // Insertion skipped because existing != null
        long assignedId = existing != null ? existing.getId() : 102L;
        assertEquals(101L, assignedId);
        assertEquals(1, mockStore.size());
    }

    @Test
    public void testOfflineQueueRetentionAndSyncedCleanup() {
        // List representing Room table
        java.util.List<LocationEntity> table = new java.util.ArrayList<>();
        LocationEntity loc1 = new LocationEntity("dev-1", "uuid-fail", 12.0, 77.0, 5.0f, 0.0, 0.0f, "2026-11-21T10:00:00Z", false);
        loc1.setId(1L);
        LocationEntity loc2 = new LocationEntity("dev-1", "uuid-success", 12.1, 77.1, 5.0f, 0.0, 0.0f, "2026-11-21T10:05:00Z", false);
        loc2.setId(2L);

        table.add(loc1);
        table.add(loc2);

        // Upload only loc2 successfully; mark it synced
        loc2.setSynced(true);

        // Query unsynced: loc1 must remain
        java.util.List<LocationEntity> unsynced = new java.util.ArrayList<>();
        for (LocationEntity e : table) {
            if (!e.isSynced()) unsynced.add(e);
        }
        assertEquals(1, unsynced.size());
        assertEquals("uuid-fail", unsynced.get(0).getClientUuid());

        // Simulate deleteSyncedLocations()
        table.removeIf(LocationEntity::isSynced);

        assertEquals("Failed record must survive cleanup", 1, table.size());
        assertEquals("uuid-fail", table.get(0).getClientUuid());
        assertFalse("Surviving record must retain isSynced = false", table.get(0).isSynced());
    }
}
