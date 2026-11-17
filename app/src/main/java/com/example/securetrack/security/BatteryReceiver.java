package com.example.securetrack.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;

public class BatteryReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "battery_prefs";
    private static final String KEY_ALERT_SENT = "low_battery_alert_sent";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level != -1 && scale != -1) {
                int batteryPct = (int) ((level / (float) scale) * 100);
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean alertSent = prefs.getBoolean(KEY_ALERT_SENT, false);

                if (batteryPct <= 20 && !alertSent) {
                    sendLowBatteryAlert(context, batteryPct);
                    prefs.edit().putBoolean(KEY_ALERT_SENT, true).apply();
                } else if (batteryPct > 25 && alertSent) {
                    // Reset when battery is charged enough above the threshold
                    prefs.edit().putBoolean(KEY_ALERT_SENT, false).apply();
                }
            }
        }
    }

    private void sendLowBatteryAlert(Context context, int batteryPct) {
        SessionManager sessionManager = new SessionManager(context.getApplicationContext());
        String deviceId = sessionManager.getPrimaryDeviceIdStr();
        if (deviceId == null) return;

        SecurityRepository repo = new SecurityRepository(context.getApplicationContext());
        SecurityEvent event = new SecurityEvent(
                deviceId,
                "LOW_BATTERY",
                "WARNING",
                "Device battery is extremely low (" + batteryPct + "%)"
        );

        repo.recordSecurityEvent(event, new SecurityRepository.ApiCallback<SecurityEvent>() {
            @Override
            public void onSuccess(SecurityEvent result) {}

            @Override
            public void onError(String errorMessage) {}
        });
    }
}
