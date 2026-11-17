package com.example.securetrack.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class SessionManager {

    private static final String PREF_NAME = "securetrack_session";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_BACKGROUND_TRACKING = "background_tracking_enabled";
    private static final String KEY_PRIMARY_DEVICE_ID = "primary_device_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty();
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public String getInstallationId() {
        String id = prefs.getString(KEY_INSTALLATION_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
        }
        return id;
    }

    public String getBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "http://127.0.0.1:8080/");
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

    public void logout() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .apply();
    }
}
