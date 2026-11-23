package com.example.securetrack.security;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;
import com.example.securetrack.utils.SessionManager;

/**
 * Biometric Power Protection Service for SecureTrack.
 *
 * Platform Capabilities & Security Guardrails:
 * 1. Third-party Android apps cannot reliably intercept system power menus to universally block
 *    hardware switch-off/reboot without root or Accessibility abuse.
 * 2. Authenticates sensitive power and lockdown actions via BiometricPrompt.
 * 3. Detects Android Device Owner status at runtime (Device Admin != Device Owner).
 * 4. Captures impending shutdown broadcasts (ACTION_SHUTDOWN / ACTION_QUICKBOOT_POWEROFF)
 *    to record a high-priority telemetry checkpoint before power is lost.
 */
public class BiometricPowerProtectionManager {

    private static final String TAG = "PowerProtection";
    private static volatile BiometricPowerProtectionManager instance;
    private final Context context;
    private final SessionManager sessionManager;

    public enum CapabilityState {
        POWER_PROTECTION_ENABLED,
        DEVICE_OWNER_CAPABLE,
        BEST_EFFORT_MONITORING,
        UNSUPPORTED
    }

    public static BiometricPowerProtectionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BiometricPowerProtectionManager.class) {
                if (instance == null) {
                    instance = new BiometricPowerProtectionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public BiometricPowerProtectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = new SessionManager(context);
    }

    /**
     * Checks if this application is provisioned as an Android Device Owner.
     */
    public boolean isDeviceOwner() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null) {
                return dpm.isDeviceOwnerApp(context.getPackageName());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking device owner state: " + e.getMessage());
        }
        return false;
    }

    /**
     * Evaluates current platform capability state for power protection.
     */
    public CapabilityState getCapabilityState() {
        if (!BiometricAuthHelper.canAuthenticate(context)) {
            return CapabilityState.UNSUPPORTED;
        }

        if (sessionManager.isPowerProtectionEnabled()) {
            if (isDeviceOwner()) {
                return CapabilityState.DEVICE_OWNER_CAPABLE;
            }
            return CapabilityState.POWER_PROTECTION_ENABLED;
        }

        return CapabilityState.BEST_EFFORT_MONITORING;
    }

    /**
     * Authenticates an impending sensitive power or administrative action.
     */
    public void authenticateSensitiveAction(
            @NonNull FragmentActivity activity,
            @NonNull String actionTitle,
            @NonNull BiometricAuthHelper.AuthCallback callback
    ) {
        if (!sessionManager.isPowerProtectionEnabled()) {
            callback.onAuthSuccess();
            return;
        }

        BiometricAuthHelper.authenticate(
                activity,
                actionTitle,
                "Authenticate using fingerprint, face, or device PIN to confirm",
                callback
        );
    }

    /**
     * Records telemetry checkpoint before shutdown.
     */
    public void recordImpendingShutdownEvent() {
        Log.i(TAG, "Recording impending device shutdown telemetry checkpoint.");
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int level = -1;
            if (batteryStatus != null) {
                int rawLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (rawLevel >= 0 && scale > 0) {
                    level = Math.round((rawLevel / (float) scale) * 100);
                }
            }
            Log.i(TAG, "Battery level at shutdown: " + level + "%");
        } catch (Exception e) {
            Log.w(TAG, "Could not capture battery state at shutdown: " + e.getMessage());
        }
    }
}
