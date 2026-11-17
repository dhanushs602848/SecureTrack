package com.example.securetrack.security;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;

public class SimChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("android.intent.action.SIM_STATE_CHANGED".equals(action)) {
            SessionManager sessionManager = new SessionManager(context.getApplicationContext());
            String deviceIdStr = sessionManager.getPrimaryDeviceIdStr();
            if (deviceIdStr == null) return;

            SecurityRepository repo = new SecurityRepository(context.getApplicationContext());
            SecurityEvent event = new SecurityEvent(
                    deviceIdStr,
                    "SIM_CARD_EVENT",
                    "WARNING",
                    "SIM state change detected on device"
            );

            repo.recordSecurityEvent(event, new SecurityRepository.ApiCallback<SecurityEvent>() {
                @Override
                public void onSuccess(SecurityEvent result) {}

                @Override
                public void onError(String errorMessage) {}
            });
        }
    }
}
