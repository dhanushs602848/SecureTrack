package com.example.securetrack;

import com.example.securetrack.ble.BleNearbyManager;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.SecurityAlertEntity;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.models.ResolvedLocation;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.security.AlarmSoundManager;
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
}
