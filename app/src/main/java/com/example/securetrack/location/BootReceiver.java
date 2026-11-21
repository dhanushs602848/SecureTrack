package com.example.securetrack.location;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.securetrack.MainActivity;
import com.example.securetrack.R;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;

public class BootReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "securetrack_boot_channel";
    private static final int NOTIFICATION_ID = 2002;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SessionManager sessionManager = new SessionManager(context.getApplicationContext());

            // Check if user previously enabled tracking
            if (sessionManager.isBackgroundTrackingEnabled()) {
                showBootNotification(context, "SecureTrack protection was paused after device reboot. Tap to reopen and resume protection.");
            }
        }
    }

    private void showBootNotification(Context context, String message) {
        if (!PermissionUtils.hasNotificationPermission(context)) {
            return;
        }

        createNotificationChannel(context);

        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_logo)
                .setContentTitle("SecureTrack Reboot Notice")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.notify(NOTIFICATION_ID, builder.build());
            } catch (SecurityException e) {
                // Permission revoked
            }
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SecureTrack System Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
