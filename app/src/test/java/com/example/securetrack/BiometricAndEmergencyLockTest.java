package com.example.securetrack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import com.example.securetrack.models.DeviceCommand;
import com.example.securetrack.utils.SessionManager;
import org.junit.Before;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BiometricAndEmergencyLockTest {

    private FakeSharedPreferences fakePrefs;
    private SessionManager sessionManager;

    @Before
    public void setUp() {
        fakePrefs = new FakeSharedPreferences();
        sessionManager = new SessionManager(fakePrefs);
    }

    @Test
    public void testBiometricSettingDefaultAndToggle() {
        assertFalse("Biometric lock should default to false", sessionManager.isBiometricLockEnabled());

        sessionManager.setBiometricLockEnabled(true);
        assertTrue("Biometric lock should return true when set", sessionManager.isBiometricLockEnabled());

        sessionManager.setBiometricLockEnabled(false);
        assertFalse("Biometric lock should return false when unset", sessionManager.isBiometricLockEnabled());
    }

    @Test
    public void testEmergencyRecoveryMessageAndContact() {
        sessionManager.setEmergencyContact("+919876543210");
        sessionManager.setLockMessage("Please call owner if lost.");

        assertEquals("+919876543210", sessionManager.getEmergencyContact());
        assertEquals("Please call owner if lost.", sessionManager.getLockMessage());
    }

    @Test
    public void testEmergencyLockCommandCreation() {
        DeviceCommand cmd = new DeviceCommand("LOCK_DEVICE", "QUEUED", "Remote lock emergency", "Pending");
        assertEquals("LOCK_DEVICE", cmd.getCommandType());
        assertEquals("QUEUED", cmd.getStatus());
        assertEquals("Remote lock emergency", cmd.getPayload());
        assertEquals("Pending", cmd.getResult());
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
            Object val = map.get(key);
            return val instanceof String ? (String) val : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }

        @Override
        public int getInt(String key, int defValue) {
            Object val = map.get(key);
            return val instanceof Integer ? (Integer) val : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object val = map.get(key);
            return val instanceof Long ? (Long) val : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object val = map.get(key);
            return val instanceof Float ? (Float) val : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object val = map.get(key);
            return val instanceof Boolean ? (Boolean) val : defValue;
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
        public Editor putStringSet(String key, Set<String> values) { map.put(key, values); return this; }

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
