package com.example.securetrack.security;

import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class LocationSecurityManager {
    private static final String TAG = "LocationSecurityMgr";
    private static final String PREF_LAST_LOCATION_STATE = "last_location_state";
    private static final int STATE_UNKNOWN = -1;
    private static final int STATE_DISABLED = 0;
    private static final int STATE_ENABLED = 1;

    private static LocationSecurityManager instance;
    private final Context context;
    private final SecurityRepository securityRepository;
    private final SessionManager sessionManager;
    private final FirebaseFirestore db;
    private final android.content.SharedPreferences prefs;

    private LocationSecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.securityRepository = new SecurityRepository(this.context);
        this.sessionManager = new SessionManager(this.context);
        this.db = FirebaseFirestore.getInstance();
        this.prefs = this.context.getSharedPreferences("LocationSecurityPrefs", Context.MODE_PRIVATE);
    }

    public static synchronized LocationSecurityManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocationSecurityManager(context);
        }
        return instance;
    }

    public void checkLocationState() {
        if (!sessionManager.isLoggedIn()) return;

        String deviceId = sessionManager.getCurrentDeviceId();
        if (deviceId == null) return;

        boolean isLocationEnabled = isLocationEnabled();
        int currentState = isLocationEnabled ? STATE_ENABLED : STATE_DISABLED;

        // Scope state per device installation ID to ensure multi-device isolation
        String stateKey = PREF_LAST_LOCATION_STATE + "_" + deviceId;
        int lastState = prefs.getInt(stateKey, STATE_UNKNOWN);

        Log.d(TAG, "Checking location state for device=" + deviceId + ", current=" + currentState + ", last=" + lastState);

        if (lastState != STATE_UNKNOWN && lastState != currentState) {
            if (currentState == STATE_DISABLED) {
                reportLocationDisabled(deviceId);
            } else {
                reportLocationEnabled(deviceId);
            }
        } else if (lastState == STATE_UNKNOWN) {
            // First run on this installation: sync initial location status to device document
            updateDeviceLocationStatus(deviceId, isLocationEnabled ? "ENABLED" : "DISABLED");
        }

        prefs.edit().putInt(stateKey, currentState).apply();
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            int locationMode = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
            );
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
    }

    private void reportLocationDisabled(String deviceId) {
        String deviceName = sessionManager.getDeviceName();
        SecurityEvent event = new SecurityEvent(
                deviceId,
                deviceName,
                "LOCATION_DISABLED",
                "HIGH",
                "Device location was turned off by the user on " + deviceName + ".",
                "DISABLED"
        );
        sendAlert(event);
        updateDeviceLocationStatus(deviceId, "DISABLED");
    }

    private void reportLocationEnabled(String deviceId) {
        String deviceName = sessionManager.getDeviceName();
        SecurityEvent event = new SecurityEvent(
                deviceId,
                deviceName,
                "LOCATION_ENABLED",
                "INFO",
                "Device location was turned back on on " + deviceName + ".",
                "ENABLED"
        );
        sendAlert(event);
        updateDeviceLocationStatus(deviceId, "ENABLED");
    }

    private void updateDeviceLocationStatus(String deviceId, String status) {
        String uid = sessionManager.getOwnerUid();
        if (uid == null || deviceId == null) return;

        Map<String, Object> map = new HashMap<>();
        map.put("locationStatus", status);
        map.put("lastSeen", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .collection("devices").document(deviceId)
                .set(map, SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update device locationStatus: " + e.getMessage()));
    }

    private void sendAlert(SecurityEvent event) {
        Log.d(TAG, "Sending security alert for device " + event.getDeviceId() + ": " + event.getEventType());
        securityRepository.recordSecurityEvent(event, new SecurityRepository.ApiCallback<SecurityEvent>() {
            @Override
            public void onSuccess(SecurityEvent result) {
                Log.d(TAG, "Successfully reported " + result.getEventType() + " for device " + result.getDeviceId());
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed to report location state: " + errorMessage);
            }
        });
    }
}
