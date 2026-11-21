package com.example.securetrack.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.google.firebase.auth.FirebaseAuth;
import java.util.UUID;

public class SessionManager {

    private static final String PREF_NAME = "securetrack_session";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FIREBASE_UID = "firebase_uid";
    private static final String KEY_OWNER_UID = "owner_uid";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_CURRENT_DEVICE_ID = "current_device_id";
    private static final String KEY_CURRENT_DEVICE_NAME = "current_device_name";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_BACKGROUND_TRACKING = "background_tracking_enabled";
    private static final String KEY_PRIMARY_DEVICE_ID = "primary_device_id";
    private static final String KEY_PRIMARY_DEVICE_ID_STR = "primary_device_id_str";
    private static final String KEY_EMERGENCY_CONTACT = "emergency_contact";
    private static final String KEY_LAST_USER_EXIT_PROMPT_TIMESTAMP = "last_user_exit_prompt_ts";
    private static final String KEY_LOCK_MESSAGE = "lock_message";
    private static final String KEY_SIM_ALERT = "sim_alert_enabled";
    private static final String KEY_LOW_BATTERY_ALERT = "low_battery_alert_enabled";
    private static final String KEY_IS_PAIRED_DEVICE = "is_paired_secondary_device";
    private static final String KEY_PAIRED_OWNER_UID = "paired_owner_uid";

    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK = "DARK";
    public static final String THEME_SYSTEM = "SYSTEM";
    private static final String KEY_THEME_MODE = "app_theme_mode";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public SessionManager(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public void saveAuthSession(String accessToken, String refreshToken, long userId, String email, String fullName) {
        prefs.edit()
                .putString(KEY_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, fullName)
                .apply();
    }

    public void saveFirebaseAuthSession(String uid, String email, String fullName) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_FIREBASE_UID, uid)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, fullName);
        if (!isPairedSecondaryDevice()) {
            editor.putString(KEY_OWNER_UID, uid);
        }
        editor.apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getFirebaseUid() {
        String uid = prefs.getString(KEY_FIREBASE_UID, null);
        if (uid == null) {
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                }
            } catch (Exception ignored) {}
        }
        return uid;
    }

    public boolean isPairedSecondaryDevice() {
        return prefs.getBoolean(KEY_IS_PAIRED_DEVICE, false);
    }

    public void setPairedSecondaryDevice(boolean isPaired) {
        prefs.edit().putBoolean(KEY_IS_PAIRED_DEVICE, isPaired).apply();
    }

    public String getPairedOwnerUid() {
        return prefs.getString(KEY_PAIRED_OWNER_UID, null);
    }

    public void setPairedOwnerUid(String ownerUid) {
        prefs.edit().putString(KEY_PAIRED_OWNER_UID, ownerUid).apply();
    }

    /**
     * Authenticated human owner UID.
     * Guaranteed to return only the verified account owner UID.
     * If this device is an enrolled secondary device, returns the paired account owner UID,
     * preventing enrolled devices from overwriting or falling back to their own auth UID.
     */
    public String getOwnerUid() {
        if (isPairedSecondaryDevice()) {
            String paired = getPairedOwnerUid();
            if (paired != null && !paired.isEmpty()) {
                return paired;
            }
        }
        return prefs.getString(KEY_OWNER_UID, null);
    }

    public void setOwnerUid(String ownerUid) {
        if (isPairedSecondaryDevice()) {
            String paired = getPairedOwnerUid();
            if (paired != null && !paired.isEmpty()) {
                prefs.edit().putString(KEY_OWNER_UID, paired).apply();
                return;
            }
        }
        prefs.edit().putString(KEY_OWNER_UID, ownerUid).apply();
    }

    public boolean isLoggedIn() {
        boolean hasFirebaseUser = false;
        try {
            hasFirebaseUser = (FirebaseAuth.getInstance().getCurrentUser() != null);
        } catch (Exception ignored) {}
        hasFirebaseUser = hasFirebaseUser || (getFirebaseUid() != null);
        boolean hasBackendToken = getToken() != null && !getToken().isEmpty();
        return hasFirebaseUser || hasBackendToken;
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getUserEmail() {
        String email = prefs.getString(KEY_USER_EMAIL, "");
        if (email.isEmpty()) {
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                }
            } catch (Exception ignored) {}
            if (email == null) email = "";
        }
        return email;
    }

    public String getUserName() {
        String name = prefs.getString(KEY_USER_NAME, "");
        if (name.isEmpty()) {
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    name = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
                }
            } catch (Exception ignored) {}
            if (name == null || name.isEmpty()) {
                name = getUserEmail();
            }
        }
        return name;
    }

    /**
     * Unique, persistent installation ID for this physical device.
     * Guaranteed to persist across logout and app restarts.
     */
    public String getInstallationId() {
        String id = prefs.getString(KEY_INSTALLATION_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
        }
        return id;
    }

    public void setInstallationId(String id) {
        prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
    }

    /**
     * Returns this phone's actual registered Firestore device document ID.
     * Matches the installation ID or assigned registered document ID for this physical installation.
     */
    public String getCurrentDeviceId() {
        String id = prefs.getString(KEY_CURRENT_DEVICE_ID, null);
        if (id == null) {
            id = getPrimaryDeviceIdStr();
        }
        return id != null ? id : getInstallationId();
    }

    public void setCurrentDeviceId(String deviceId) {
        prefs.edit()
                .putString(KEY_CURRENT_DEVICE_ID, deviceId)
                .putString(KEY_PRIMARY_DEVICE_ID_STR, deviceId)
                .apply();
    }

    public String getDeviceName() {
        return prefs.getString(KEY_CURRENT_DEVICE_NAME, Build.MODEL != null ? Build.MODEL : "Android Device");
    }

    public void setDeviceName(String name) {
        prefs.edit().putString(KEY_CURRENT_DEVICE_NAME, name).apply();
    }

    public String getBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "https://track-99c90.web.app/");
    }

    public void setBaseUrl(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    public boolean isBackgroundTrackingEnabled() {
        return prefs.getBoolean(KEY_BACKGROUND_TRACKING, false);
    }

    public void setBackgroundTrackingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BACKGROUND_TRACKING, enabled).apply();
    }

    public long getPrimaryDeviceId() {
        return prefs.getLong(KEY_PRIMARY_DEVICE_ID, -1);
    }

    public void setPrimaryDeviceId(long deviceId) {
        prefs.edit().putLong(KEY_PRIMARY_DEVICE_ID, deviceId).apply();
    }

    public String getPrimaryDeviceIdStr() {
        String id = prefs.getString(KEY_PRIMARY_DEVICE_ID_STR, null);
        if (id == null) {
            id = prefs.getString(KEY_CURRENT_DEVICE_ID, null);
        }
        if (id == null) {
            long oldId = getPrimaryDeviceId();
            if (oldId != -1) {
                id = String.valueOf(oldId);
            } else {
                id = getInstallationId();
            }
        }
        return id;
    }

    public void setPrimaryDeviceIdStr(String deviceId) {
        setCurrentDeviceId(deviceId);
    }

    public String getEmergencyContact() {
        return prefs.getString(KEY_EMERGENCY_CONTACT, "");
    }

    public void setEmergencyContact(String contact) {
        prefs.edit().putString(KEY_EMERGENCY_CONTACT, contact).apply();
    }

    public String getLockMessage() {
        return prefs.getString(KEY_LOCK_MESSAGE, "This phone is protected by SecureTrack. If found, please return it.");
    }

    public void setLockMessage(String message) {
        prefs.edit().putString(KEY_LOCK_MESSAGE, message).apply();
    }

    public long getLastUserExitPromptTimestamp() {
        return prefs.getLong(KEY_LAST_USER_EXIT_PROMPT_TIMESTAMP, 0L);
    }

    public void setLastUserExitPromptTimestamp(long timestamp) {
        prefs.edit().putLong(KEY_LAST_USER_EXIT_PROMPT_TIMESTAMP, timestamp).apply();
    }

    public boolean isSimAlertEnabled() {
        return prefs.getBoolean(KEY_SIM_ALERT, true);
    }

    public void setSimAlertEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SIM_ALERT, enabled).apply();
    }

    public boolean isLowBatteryAlertEnabled() {
        return prefs.getBoolean(KEY_LOW_BATTERY_ALERT, true);
    }

    public void setLowBatteryAlertEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOW_BATTERY_ALERT, enabled).apply();
    }

    private static final String KEY_BLE_SHARED_SECRET = "ble_shared_secret";
    private static final String KEY_BLE_DEVICE_SECRETS_PREFIX = "ble_secret_dev_";

    public String getBleSharedSecret() {
        return prefs.getString(KEY_BLE_SHARED_SECRET, null);
    }

    public void setBleSharedSecret(String secret) {
        prefs.edit().putString(KEY_BLE_SHARED_SECRET, secret).apply();
    }

    public String getBleDeviceSecret(String deviceId) {
        if (deviceId == null) return null;
        String secret = prefs.getString(KEY_BLE_DEVICE_SECRETS_PREFIX + deviceId, null);
        if (secret == null) {
            // Fallback to primary shared secret
            return getBleSharedSecret();
        }
        return secret;
    }

    public void saveBleDeviceSecret(String deviceId, String secret) {
        if (deviceId == null || secret == null) return;
        prefs.edit().putString(KEY_BLE_DEVICE_SECRETS_PREFIX + deviceId, secret).apply();
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public void setThemeMode(String themeMode) {
        if (themeMode == null) themeMode = THEME_SYSTEM;
        prefs.edit().putString(KEY_THEME_MODE, themeMode).apply();
        applyTheme(themeMode);
    }

    public static void applyTheme(String themeMode) {
        if (THEME_LIGHT.equalsIgnoreCase(themeMode)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_DARK.equalsIgnoreCase(themeMode)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static void applySavedTheme(Context context) {
        SessionManager session = new SessionManager(context);
        applyTheme(session.getThemeMode());
    }

    public void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}

        // Clear user session data, but NEVER clear KEY_INSTALLATION_ID so device retains its identity
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_FIREBASE_UID)
                .remove(KEY_OWNER_UID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_CURRENT_DEVICE_ID)
                .remove(KEY_PRIMARY_DEVICE_ID_STR)
                .remove(KEY_IS_PAIRED_DEVICE)
                .remove(KEY_PAIRED_OWNER_UID)
                .apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
