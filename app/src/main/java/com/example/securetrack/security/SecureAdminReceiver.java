package com.example.securetrack.security;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;

public class SecureAdminReceiver extends DeviceAdminReceiver {

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context.getApplicationContext(), SecureAdminReceiver.class);
    }

    public static boolean isAdminActive(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(getComponentName(context));
    }

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "SecureTrack Device Admin Activated", Toast.LENGTH_SHORT).show();
        recordAdminEvent(context, "DEVICE_ADMIN_ENABLED", "HIGH", "Device Administrator privileges granted by user");
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "SecureTrack Device Admin Deactivated", Toast.LENGTH_SHORT).show();
        recordAdminEvent(context, "DEVICE_ADMIN_DISABLED", "CRITICAL", "Device Administrator privileges deactivated!");
    }

    private void recordAdminEvent(Context context, String eventType, String severity, String desc) {
        SessionManager sessionManager = new SessionManager(context.getApplicationContext());
        long deviceId = sessionManager.getPrimaryDeviceId();
        if (deviceId == -1) return;

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
