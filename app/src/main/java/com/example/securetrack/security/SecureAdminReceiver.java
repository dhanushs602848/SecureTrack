package com.example.securetrack.security;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;

public class SecureAdminReceiver extends DeviceAdminReceiver {

    public static boolean isAdminActive(Context context) {
        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName adminComponent = new android.content.ComponentName(context, SecureAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(adminComponent);
    }

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        logSecurityEvent(context, "DEVICE_ADMIN_ENABLED", "INFO", "Device Administrator privileges granted");
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        logSecurityEvent(context, "DEVICE_ADMIN_DISABLED", "WARNING", "Device Administrator privileges revoked");
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show();
    }

    private void logSecurityEvent(Context context, String eventType, String severity, String desc) {
        SessionManager sessionManager = new SessionManager(context.getApplicationContext());
        String deviceId = sessionManager.getPrimaryDeviceIdStr();

        if (deviceId == null) return;

        SecurityRepository repo = new SecurityRepository(context.getApplicationContext());
        SecurityEvent event = new SecurityEvent(deviceId, eventType, severity, desc);

        repo.recordSecurityEvent(event, new SecurityRepository.ApiCallback<SecurityEvent>() {
            @Override
            public void onSuccess(SecurityEvent result) {}

            @Override
            public void onError(String errorMessage) {}
        });
    }
}
