package com.example.securetrack.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.SharedPreferences;
import com.example.securetrack.receivers.ShutdownReceiver;
import com.example.securetrack.utils.SessionManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class BiometricPowerProtectionManagerTest {

    private FakeSharedPreferences fakePrefs;
    private SessionManager sessionManager;
    private BiometricPowerProtectionManager manager;

    @Before
    public void setUp() {
        fakePrefs = new FakeSharedPreferences();
        sessionManager = new SessionManager(fakePrefs);
        manager = new BiometricPowerProtectionManager(null, sessionManager);
    }

    @Test
    public void testPowerProtectionPreferencePersistence() {
        assertFalse("Power protection should default to false", sessionManager.isPowerProtectionEnabled());

        sessionManager.setPowerProtectionEnabled(true);
        assertTrue("Power protection should return true when enabled", sessionManager.isPowerProtectionEnabled());
        assertTrue("Manager should reflect enabled state from session", manager.isPowerProtectionEnabled());

        sessionManager.setPowerProtectionEnabled(false);
        assertFalse("Power protection should return false when disabled", sessionManager.isPowerProtectionEnabled());
        assertFalse("Manager should reflect disabled state from session", manager.isPowerProtectionEnabled());
    }

    @Test
    public void testGeofenceLostModePersistence() {
        assertFalse("Geofence lost mode should default to false", sessionManager.isGeofenceLostMode());

        sessionManager.setGeofenceLostMode(true);
        assertTrue("Geofence lost mode should be true when set", sessionManager.isGeofenceLostMode());

        sessionManager.setGeofenceLostMode(false);
        assertFalse("Geofence lost mode should be false when cleared", sessionManager.isGeofenceLostMode());
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

    @Test
    public void testCapabilityEvaluationWithNullContext() {
        BiometricPowerProtectionManager.CapabilityState state = manager.getCapabilityState();
        assertNotNull("Capability state must not be null", state);
        assertEquals("Capability state without hardware context must be UNSUPPORTED",
                BiometricPowerProtectionManager.CapabilityState.UNSUPPORTED, state);
    }

    @Test
    public void testShutdownReceiverNullSafety() {
        ShutdownReceiver receiver = new ShutdownReceiver();
        // Should handle null context or null intent without throwing uncaught exceptions
        receiver.onReceive(null, null);
        receiver.onReceive(null, new Intent(Intent.ACTION_SHUTDOWN));
        receiver.onReceive(null, new Intent("android.intent.action.QUICKBOOT_POWEROFF"));
    }

    @Test
    public void testManagerImpendingShutdownNullContextSafety() {
        // Safe handling when context is null
        manager.recordImpendingShutdownEvent();
    }

    /**
     * Minimal in-memory SharedPreferences implementation for hermetic unit testing.
     */
    private static class FakeSharedPreferences implements SharedPreferences, SharedPreferences.Editor {
        private final Map<String, Object> map = new HashMap<>();

        @Override
        public Map<String, ?> getAll() { return new HashMap<>(map); }

        @Override
        public String getString(String key, String defValue) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }

        @Override
        public int getInt(String key, int defValue) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = map.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object v = map.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public boolean contains(String key) { return map.containsKey(key); }

        @Override
        public Editor edit() { return this; }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public Editor putString(String key, String value) { map.put(key, value); return this; }

        @Override
        public Editor putStringSet(String key, Set<String> values) { return this; }

        @Override
        public Editor putInt(String key, int value) { map.put(key, value); return this; }

        @Override
        public Editor putLong(String key, long value) { map.put(key, value); return this; }

        @Override
        public Editor putFloat(String key, float value) { map.put(key, value); return this; }

        @Override
        public Editor putBoolean(String key, boolean value) { map.put(key, value); return this; }

        @Override
        public Editor remove(String key) { map.remove(key); return this; }

        @Override
        public Editor clear() { map.clear(); return this; }

        @Override
        public boolean commit() { return true; }

        @Override
        public void apply() {}
    }
}
