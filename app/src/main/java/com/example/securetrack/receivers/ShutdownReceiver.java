package com.example.securetrack.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.securetrack.security.BiometricPowerProtectionManager;

/**
 * BroadcastReceiver listening for impending system shutdown events.
 * Captures final telemetry snapshot before device loses power.
 */
public class ShutdownReceiver extends BroadcastReceiver {
    private static final String TAG = "ShutdownReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "System shutdown broadcast received: " + action);

        if (Intent.ACTION_SHUTDOWN.equals(action) ||
            "android.intent.action.QUICKBOOT_POWEROFF".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWEROFF".equals(action)) {

            try {
                BiometricPowerProtectionManager.getInstance(context).recordImpendingShutdownEvent();
            } catch (Exception e) {
                Log.e(TAG, "Error handling shutdown event", e);
            }
        }
    }
}
