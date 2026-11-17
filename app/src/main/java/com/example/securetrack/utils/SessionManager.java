package com.example.securetrack.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import java.util.UUID;

public class SessionManager {

    private static final String PREF_NAME = "securetrack_session";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FIREBASE_UID = "firebase_uid";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_BACKGROUND_TRACKING = "background_tracking_enabled";
    private static final String KEY_PRIMARY_DEVICE_ID = "primary_device_id";
    private static final String KEY_PRIMARY_DEVICE_ID_STR = "primary_device_id_str";

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

    public void saveFirebaseAuthSession(String uid, String email, String fullName) {
        prefs.edit()
                .putString(KEY_FIREBASE_UID, uid)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, fullName)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getFirebaseUid() {
        String uid = prefs.getString(KEY_FIREBASE_UID, null);
        if (uid == null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return uid;
    }

    public boolean isLoggedIn() {
        boolean hasFirebaseUser = FirebaseAuth.getInstance().getCurrentUser() != null || getFirebaseUid() != null;
        boolean hasBackendToken = getToken() != null && !getToken().isEmpty();
        return hasFirebaseUser || hasBackendToken;
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getUserEmail() {
        String email = prefs.getString(KEY_USER_EMAIL, "");
        if (email.isEmpty() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email == null) email = "";
        }
        return email;
    }

    public String getUserName() {
        String name = prefs.getString(KEY_USER_NAME, "");
        if (name.isEmpty() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            name = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            if (name == null || name.isEmpty()) {
                name = getUserEmail();
            }
        }
        return name;
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

    public String getPrimaryDeviceIdStr() {
        String id = prefs.getString(KEY_PRIMARY_DEVICE_ID_STR, null);
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
        prefs.edit().putString(KEY_PRIMARY_DEVICE_ID_STR, deviceId).apply();
    }

    public void logout() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}

        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_FIREBASE_UID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_PRIMARY_DEVICE_ID_STR)
                .apply();
    }
}
