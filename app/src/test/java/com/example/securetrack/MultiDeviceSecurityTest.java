package com.example.securetrack;

import com.example.securetrack.models.Device;
import com.example.securetrack.models.SecurityEvent;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class MultiDeviceSecurityTest {

    @Test
    public void testDistinctDeviceIdentitiesPerInstallation() {
        // Simulate two separate physical phone installations
        String installIdPhone1 = UUID.randomUUID().toString();
        String installIdPhone2 = UUID.randomUUID().toString();

        assertNotEquals("Each installation must possess a unique ID", installIdPhone1, installIdPhone2);

        Device phone1 = new Device(
                installIdPhone1,
                "Samsung Galaxy S24",
                "SM-S928B",
                "Android 14",
                85,
                "ONLINE",
                true,
                installIdPhone1
        );

        Device phone2 = new Device(
                installIdPhone2,
                "OnePlus 12",
                "CPH2581",
                "Android 14",
                92,
                "ONLINE",
                true,
                installIdPhone2
        );

        // Verify independent attributes
        assertNotEquals(phone1.getId(), phone2.getId());
        assertNotEquals(phone1.getInstallationId(), phone2.getInstallationId());
        assertNotEquals(phone1.getDeviceName(), phone2.getDeviceName());
    }

    @Test
    public void testIndependentLocationAlertGeneration() {
        String phone1Id = "device-uuid-samsung-001";
        String phone1Name = "Samsung Galaxy S24";
        String phone2Id = "device-uuid-oneplus-002";
        String phone2Name = "OnePlus 12";

        // Phone 1 Location Disabled event
        SecurityEvent phone1Alert = new SecurityEvent(
                phone1Id,
                phone1Name,
                "LOCATION_DISABLED",
                "HIGH",
                "Device location was turned off by the user on " + phone1Name,
                "DISABLED"
        );

        // Verify alert attributes
        assertEquals(phone1Id, phone1Alert.getDeviceId());
        assertEquals(phone1Name, phone1Alert.getDeviceName());
        assertEquals("LOCATION_DISABLED", phone1Alert.getEventType());
        assertEquals("DISABLED", phone1Alert.getLocationStatus());

        // Phone 2 must NOT be impacted by Phone 1's alert
        assertNotEquals(phone2Id, phone1Alert.getDeviceId());
        assertNotEquals(phone2Name, phone1Alert.getDeviceName());

        // Phone 2 Location Restoration event
        SecurityEvent phone2Alert = new SecurityEvent(
                phone2Id,
                phone2Name,
                "LOCATION_ENABLED",
                "INFO",
                "Device location was turned back on on " + phone2Name,
                "ENABLED"
        );

        assertEquals(phone2Id, phone2Alert.getDeviceId());
        assertEquals("LOCATION_ENABLED", phone2Alert.getEventType());
        assertEquals("ENABLED", phone2Alert.getLocationStatus());
    }

    @Test
    public void testLuhnImeiValidation() {
        // Valid 15 digit IMEI passing Luhn algorithm
        String validImei = "356938035643809";
        assertTrue("Valid IMEI must pass check", isValidImei(validImei));

        // Invalid checksum
        String invalidImei = "356938035643803";
        assertFalse("Invalid checksum must fail check", isValidImei(invalidImei));

        // Non-numeric or wrong length
        assertFalse("14 digits must fail", isValidImei("35693803564380"));
        assertFalse("Alphanumeric must fail", isValidImei("35693803564380A"));
    }

    @Test
    public void testDeviceOnlineAndStaleStatus() {
        Device onlineDevice = new Device("dev1", "Pixel 8", "Pixel", "14", 90, "ONLINE", true, "dev1");
        com.google.firebase.Timestamp recentTimestamp = new com.google.firebase.Timestamp(new java.util.Date(System.currentTimeMillis() - 60000L)); // 1 min ago
        onlineDevice.setLocationTimestamp(recentTimestamp);
        onlineDevice.setLastLatitude(37.7749);
        onlineDevice.setLastLongitude(-122.4194);
        onlineDevice.setLocationAccuracy(8.5f);

        assertTrue("Recently updated online device should be online", onlineDevice.isOnline());
        assertFalse("Recent location fix should not be stale", onlineDevice.isLocationStale());

        Device offlineDevice = new Device("dev2", "Galaxy S23", "S23", "14", 45, "OFFLINE", true, "dev2");
        com.google.firebase.Timestamp oldTimestamp = new com.google.firebase.Timestamp(new java.util.Date(System.currentTimeMillis() - 25 * 60 * 1000L)); // 25 mins ago
        offlineDevice.setLocationTimestamp(oldTimestamp);
        offlineDevice.setLastLatitude(37.7749);
        offlineDevice.setLastLongitude(-122.4194);

        assertFalse("Old timestamp or OFFLINE status should make device offline", offlineDevice.isOnline());
        assertTrue("Location older than 15 mins should be stale", offlineDevice.isLocationStale());
    }

    @Test
    public void testDeviceFormattedAccuracyAndLastSeen() {
        Device dev = new Device("dev1", "Pixel 8", "Pixel", "14", 90, "ONLINE", true, "dev1");
        dev.setLocationAccuracy(12.4f);
        assertEquals("±12m", dev.getFormattedAccuracy());

        com.google.firebase.Timestamp tsFiveMinsAgo = new com.google.firebase.Timestamp(new java.util.Date(System.currentTimeMillis() - 5 * 60 * 1000L));
        dev.setLocationTimestamp(tsFiveMinsAgo);
        assertEquals("5m ago", dev.getFormattedLastSeen());
    }

    @Test
    public void testRemoteLocateCommandLifecycleAndIsolation() {
        String ownerUid = "owner-firebase-uid-100";
        String controllerDevId = "controller-phone-001";
        String targetDevId = "target-phone-002";

        double controllerLat = 37.7749;
        double controllerLng = -122.4194;

        double targetLat = 40.7128; // New York
        double targetLng = -74.0060;
        float targetAccuracy = 6.2f;
        String requestId = UUID.randomUUID().toString();

        // 1. Controller queues command for target device
        com.example.securetrack.models.DeviceCommand command = new com.example.securetrack.models.DeviceCommand("LOCATE_NOW", "QUEUED", "Remote locate from controller dashboard", "");
        command.setDeviceId(targetDevId);
        assertEquals("QUEUED", command.getStatus());
        assertEquals(targetDevId, command.getDeviceId());

        // 2. Target executes command and obtains its OWN location
        com.example.securetrack.models.LocationRecord targetLocationRecord = new com.example.securetrack.models.LocationRecord(
                targetDevId,
                requestId,
                targetLat,
                targetLng,
                targetAccuracy,
                15.0,
                0.0f,
                "2025-01-01T12:00:00Z"
        );

        // Verify target location is NOT controller's location
        assertNotEquals("Target latitude must not match controller latitude", controllerLat, targetLocationRecord.getLatitude(), 0.0001);
        assertNotEquals("Target longitude must not match controller longitude", controllerLng, targetLocationRecord.getLongitude(), 0.0001);

        // 3. Command updates status to EXECUTED
        command.setStatus("EXECUTED");
        command.setResult(String.format(java.util.Locale.US, "Location: (%.5f, %.5f) ±%.1fm [req:%s]",
                targetLat, targetLng, targetAccuracy, requestId));

        assertEquals("EXECUTED", command.getStatus());
        assertTrue(command.getResult().contains(requestId));
        assertTrue(command.getResult().contains("40.71280"));
    }

    private boolean isValidImei(String imei) {
        if (imei == null || imei.length() != 15 || !imei.matches("\\d+")) {
            return false;
        }
        int sum = 0;
        for (int i = 14; i >= 0; i--) {
            int digit = imei.charAt(i) - '0';
            if ((14 - i) % 2 != 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
