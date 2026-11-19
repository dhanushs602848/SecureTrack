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
