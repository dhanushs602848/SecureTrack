package com.example.securetrack.security;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Manages Anti-Shutdown Protection capabilities.
 *
 * Technical realities on Android:
 * 1. Consumer / Unmanaged Android devices:
 *    User-space apps cannot intercept or suppress hardware-level forced reboots/power-cuts
 *    (holding power button 8-10 seconds) or the power menu without Device Owner privileges.
 *    Any app claiming to prevent shutdown on consumer unmanaged devices without root or Device Owner
 *    is misleading.
 *
 * 2. Enterprise / Device Owner Managed devices:
 *    When enrolled as a Device Owner (DPM), the app can configure Lock Task mode with
 *    LOCK_TASK_FEATURE_NONE, which suppresses the global power options menu, status bar,
 *    and navigation keys. An authorized recovery PIN is required to exit Lock Task mode.
 */
public class AntiShutdownManager {

    private static final String TAG = "AntiShutdownManager";
    private static final String PREF_NAME = "securetrack_anti_shutdown_prefs";
    private static final String KEY_RECOVERY_PIN_HASH = "recovery_pin_hash";
    private static final String KEY_IS_ACTIVE = "anti_shutdown_active";

    public enum CapabilityStatus {
        /**
         * Standard personal or unmanaged device.
         * Device Owner is not granted. Software cannot override hardware power-cut or power menu.
         */
        UNSUPPORTED_PERSONAL_DEVICE,

        /**
         * Device Owner privilege is active and ready to lock down power menu via Lock Task mode.
         */
        SUPPORTED_DEVICE_OWNER_READY,

        /**
         * Lock Task mode is currently active with power menu suppression enforced.
         */
        ACTIVE_LOCK_TASK
    }

    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName adminComponent;
    private final SharedPreferences prefs;

    public AntiShutdownManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) this.context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.adminComponent = new ComponentName(this.context, SecureAdminReceiver.class);
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Evaluates anti-shutdown capability on the current device.
     */
    public CapabilityStatus evaluateCapability() {
        boolean isOwner = isDeviceOwner();
        boolean isLockActive = isLockTaskModeActive();
        return evaluateCapability(isOwner, isDeviceAdminActive(), isLockActive);
    }

    /**
     * Pure evaluative method for unit testing and deterministic checks.
     */
    public static CapabilityStatus evaluateCapability(boolean isDeviceOwner, boolean isAdminActive, boolean isLockTaskActive) {
        if (!isDeviceOwner) {
            return CapabilityStatus.UNSUPPORTED_PERSONAL_DEVICE;
        }
        if (isLockTaskActive) {
            return CapabilityStatus.ACTIVE_LOCK_TASK;
        }
        return CapabilityStatus.SUPPORTED_DEVICE_OWNER_READY;
    }

    /**
     * Returns a clear human-readable explanation of the capability status.
     */
    public static String getCapabilityExplanation(CapabilityStatus status) {
        switch (status) {
            case UNSUPPORTED_PERSONAL_DEVICE:
                return "Anti-Shutdown requires Android Device Owner privileges. On consumer personal devices, "
                        + "hardware power-cut (holding power button) cannot be overridden by non-owner software. "
                        + "To protect your phone, enable SIM Lock, Device Admin, and Remote Lock.";
            case SUPPORTED_DEVICE_OWNER_READY:
                return "Device Owner active. Anti-Shutdown is available and will lock down the power menu "
                        + "using Lock Task mode when enabled.";
            case ACTIVE_LOCK_TASK:
                return "Anti-Shutdown is actively running in Lock Task mode with power menu suppression. "
                        + "Enter recovery PIN to exit.";
            default:
                return "Unknown status";
        }
    }

    public boolean isDeviceOwner() {
        if (dpm == null) return false;
        try {
            return dpm.isDeviceOwnerApp(context.getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Error checking isDeviceOwnerApp", e);
            return false;
        }
    }

    public boolean isDeviceAdminActive() {
        return SecureAdminReceiver.isAdminActive(context);
    }

    public boolean isLockTaskModeActive() {
        return prefs.getBoolean(KEY_IS_ACTIVE, false);
    }

    /**
     * Enables Anti-Shutdown in Lock Task mode if Device Owner privileges are active.
     *
     * @param activity Foreground activity to lock
     * @param recoveryPin 4-8 digit recovery PIN required to exit
     * @return true if successfully enabled, false if unsupported or error
     */
    public boolean enableAntiShutdown(@NonNull Activity activity, @NonNull String recoveryPin) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot enable Anti-Shutdown: Device Owner privilege is not granted.");
            return false;
        }

        if (recoveryPin.trim().length() < 4) {
            Log.w(TAG, "Recovery PIN must be at least 4 digits");
            return false;
        }

        try {
            // Whitelist this package for Lock Task mode
            String[] packages = { context.getPackageName() };
            dpm.setLockTaskPackages(adminComponent, packages);

            // Suppress power menu, status bar, keyguard, and global actions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }

            // Save recovery PIN hash
            prefs.edit()
                    .putString(KEY_RECOVERY_PIN_HASH, hashPin(recoveryPin))
                    .putBoolean(KEY_IS_ACTIVE, true)
                    .apply();

            activity.startLockTask();
            Log.i(TAG, "Anti-Shutdown Lock Task enabled successfully with power menu suppression.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Lock Task for Anti-Shutdown", e);
            return false;
        }
    }

    /**
     * Exits Anti-Shutdown Lock Task mode after verifying the recovery PIN.
     */
    public boolean disableAntiShutdown(@NonNull Activity activity, @NonNull String inputPin) {
        if (!verifyRecoveryPin(inputPin)) {
            Log.w(TAG, "PIN verification failed for Anti-Shutdown disable.");
            return false;
        }

        try {
            activity.stopLockTask();
            prefs.edit().putBoolean(KEY_IS_ACTIVE, false).apply();
            Log.i(TAG, "Anti-Shutdown Lock Task disabled successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop Lock Task", e);
            return false;
        }
    }

    public boolean verifyRecoveryPin(@Nullable String inputPin) {
        if (inputPin == null) return false;
        String storedHash = prefs.getString(KEY_RECOVERY_PIN_HASH, null);
        if (storedHash == null) return false;
        return storedHash.equals(hashPin(inputPin));
    }

    public static String hashPin(String pin) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(pin.hashCode());
        }
    }
}
