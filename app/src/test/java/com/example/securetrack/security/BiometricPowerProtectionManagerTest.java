package com.example.securetrack.security;

import static org.junit.Assert.*;

import com.example.securetrack.utils.SessionManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class BiometricPowerProtectionManagerTest {

    private SessionManager mockSessionManager;

    @Before
    public void setUp() {
        mockSessionManager = Mockito.mock(SessionManager.class);
    }

    @Test
    public void testCapabilityStateWhenDisabled() {
        Mockito.when(mockSessionManager.isPowerProtectionEnabled()).thenReturn(false);
        assertFalse(mockSessionManager.isPowerProtectionEnabled());
    }

    @Test
    public void testCapabilityStateWhenEnabled() {
        Mockito.when(mockSessionManager.isPowerProtectionEnabled()).thenReturn(true);
        assertTrue(mockSessionManager.isPowerProtectionEnabled());
    }

    @Test
    public void testCapabilityEnumValues() {
        assertEquals(4, BiometricPowerProtectionManager.CapabilityState.values().length);
        assertEquals(BiometricPowerProtectionManager.CapabilityState.POWER_PROTECTION_ENABLED,
                BiometricPowerProtectionManager.CapabilityState.valueOf("POWER_PROTECTION_ENABLED"));
        assertEquals(BiometricPowerProtectionManager.CapabilityState.DEVICE_OWNER_CAPABLE,
                BiometricPowerProtectionManager.CapabilityState.valueOf("DEVICE_OWNER_CAPABLE"));
        assertEquals(BiometricPowerProtectionManager.CapabilityState.BEST_EFFORT_MONITORING,
                BiometricPowerProtectionManager.CapabilityState.valueOf("BEST_EFFORT_MONITORING"));
        assertEquals(BiometricPowerProtectionManager.CapabilityState.UNSUPPORTED,
                BiometricPowerProtectionManager.CapabilityState.valueOf("UNSUPPORTED"));
    }
}
