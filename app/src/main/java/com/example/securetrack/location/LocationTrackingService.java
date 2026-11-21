package com.example.securetrack.location;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.securetrack.MainActivity;
import com.example.securetrack.R;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.security.CommandExecutionManager;
import com.example.securetrack.security.LocationSecurityManager;
import com.example.securetrack.sync.DurableSyncManager;
import com.example.securetrack.utils.PermissionUtils;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Dedicated, lifecycle-independent foreground protection and location tracking service.
 *
 * Operational guarantees:
 * 1. Independent of MainActivity lifecycle — swiping app from Recents does NOT stop this service.
 * 2. Promotes itself to foreground promptly with an ongoing, honest persistent notification.
 * 3. Provides an explicit, user-accessible "Stop Protection" action button in the notification.
 * 4. Listens for cloud anti-theft commands (LOCATE_NOW, LOCK_DEVICE, ALARM_ALERT) in the background.
 * 5. Dynamically adapts when GPS or network connectivity toggles without terminating command listeners.
 * 6. Safely cleans up all callbacks, listeners, and broadcasts upon stopping.
 */
public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";

    public static final String CHANNEL_ID = "securetrack_location_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_TRACKING = "com.example.securetrack.action.START_TRACKING";
    public static final String ACTION_STOP_TRACKING = "com.example.securetrack.action.STOP_TRACKING";
    public static final String ACTION_REFRESH_STATUS = "com.example.securetrack.action.REFRESH_STATUS";

    public static volatile boolean isServiceRunning = false;

    public static boolean isServiceRunning(Context context) {
        if (isServiceRunning) return true;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (LocationTrackingService.class.getName().equals(service.service.getClassName())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRepository locationRepository;
    private SessionManager sessionManager;
    private CommandExecutionManager commandExecutionManager;
    private BroadcastReceiver locationProviderReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean isLocationUpdatesActive = false;
    private String currentStatusDescription = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Context context = getApplicationContext();
        sessionManager = new SessionManager(context);
        locationRepository = new LocationRepository(context);
        commandExecutionManager = new CommandExecutionManager(context);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        createNotificationChannel();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        handleNewLocation(location);
                    }
                }
            }
        };

        // Register receiver for GPS / location provider state changes
        registerLocationProviderReceiver();

        // Register network callback for dynamic online/offline status updates
        registerNetworkCallback();

        // Initial check of location hardware state
        LocationSecurityManager.getInstance(context).checkLocationState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP_TRACKING.equals(action)) {
            Log.i(TAG, "Stop action received via notification or intent");
            stopProtectionService();
            return START_NOT_STICKY;
        }

        // 1. Verify foreground location permission is present before attempting promotion.
        // On Android 14+ (API 34+), calling startForeground with FOREGROUND_SERVICE_TYPE_LOCATION
        // without location permission crashes with SecurityException.
        if (!PermissionUtils.hasForegroundLocationPermission(this)) {
            Log.e(TAG, "Cannot start location foreground service: Location permission is missing");
            sessionManager.setBackgroundTrackingEnabled(false);
            syncStatusToCloud("STOPPED");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 2. Determine honest operational status and build persistent notification promptly
        currentStatusDescription = resolveCurrentStatus();
        Notification notification = buildNotification(currentStatusDescription);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            isServiceRunning = true;
        } catch (Exception e) {
            isServiceRunning = false;
            Log.e(TAG, "Foreground service promotion failed", e);
            sessionManager.setBackgroundTrackingEnabled(false);
            syncStatusToCloud("STOPPED");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 3. Foreground promotion succeeded: listen to Firestore real-time commands
        if (commandExecutionManager != null) {
            commandExecutionManager.startListeningToCommands();
        }

        // 4. Start or pause location updates based on hardware GPS status
        evaluateAndApplyLocationUpdates();

        return START_STICKY;
    }

    private void evaluateAndApplyLocationUpdates() {
        boolean hasPermission = PermissionUtils.hasForegroundLocationPermission(this);
        boolean isGpsEnabled = PermissionUtils.isLocationServiceEnabled(this);

        if (hasPermission && isGpsEnabled) {
            startLocationUpdates();
        } else {
            pauseLocationUpdates();
        }
        updateNotification(resolveCurrentStatus());
        syncStatusToCloud("ACTIVE");
    }

    private synchronized void startLocationUpdates() {
        if (isLocationUpdatesActive) return;

        if (!PermissionUtils.hasForegroundLocationPermission(this)) {
            Log.w(TAG, "Cannot start location updates: Foreground location permission missing");
            return;
        }

        try {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                    .setMinUpdateIntervalMillis(5000)
                    .build();

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            isLocationUpdatesActive = true;
            Log.i(TAG, "Continuous FusedLocation updates initiated");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException requesting location updates", e);
            isLocationUpdatesActive = false;
        }
    }

    private synchronized void pauseLocationUpdates() {
        if (!isLocationUpdatesActive) return;

        try {
            if (fusedLocationClient != null && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing location updates", e);
        }
        isLocationUpdatesActive = false;
        Log.i(TAG, "Location updates paused");
    }

    private void handleNewLocation(@NonNull Location location) {
        String deviceIdStr = sessionManager.getCurrentDeviceId();
        if (deviceIdStr == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date(location.getTime()));

        String clientUuid = UUID.randomUUID().toString();

        LocationRecord record = new LocationRecord(
                deviceIdStr,
                clientUuid,
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getAltitude(),
                location.getSpeed(),
                timestamp
        );

        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
            @Override
            public void onSuccess(LocationRecord result) {
                // Update persistent notification with live timestamp
                updateNotification("Monitoring operational • Last GPS fix: Just now");
            }

            @Override
            public void onError(String errorMessage) {
                // Log and queue in offline Room store
                Log.w(TAG, "Error streaming live location to cloud: " + errorMessage);
            }
        });
    }

    private String resolveCurrentStatus() {
        boolean hasPermission = PermissionUtils.hasForegroundLocationPermission(this);
        boolean isGpsEnabled = PermissionUtils.isLocationServiceEnabled(this);
        boolean isOnline = DurableSyncManager.isNetworkAvailable(this);

        if (!hasPermission) {
            return getString(R.string.notification_status_permission_needed);
        }
        if (!isGpsEnabled) {
            return getString(R.string.notification_status_gps_disabled);
        }
        if (!isOnline) {
            return getString(R.string.notification_status_offline);
        }
        return getString(R.string.notification_status_active);
    }

    private Notification buildNotification(String statusText) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.putExtra("navigate_to", "tracking");
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Explicit "Stop Protection" action for transparency and user control
        Intent stopIntent = new Intent(this, LocationTrackingService.class);
        stopIntent.setAction(ACTION_STOP_TRACKING);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(statusText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(statusText))
                .setSmallIcon(R.drawable.ic_shield_logo)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notification_action_stop), stopPendingIntent)
                .build();
    }

    public void updateNotification(String statusText) {
        currentStatusDescription = statusText;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.notify(NOTIFICATION_ID, buildNotification(statusText));
            } catch (Exception e) {
                Log.e(TAG, "Error updating notification", e);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active endpoint protection and tracking status");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void registerLocationProviderReceiver() {
        locationProviderReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Location provider state changed: " + intent.getAction());
                evaluateAndApplyLocationUpdates();
                LocationSecurityManager.getInstance(context).checkLocationState();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(locationProviderReceiver, filter);
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(TAG, "Network connection restored");
                updateNotification(resolveCurrentStatus());
                DurableSyncManager.triggerImmediateSync(getApplicationContext());
                syncStatusToCloud("ACTIVE");
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.w(TAG, "Network connection lost");
                updateNotification(getString(R.string.notification_status_offline));
            }
        };

        try {
            cm.registerNetworkCallback(request, networkCallback);
        } catch (Exception e) {
            Log.e(TAG, "Error registering network callback", e);
        }
    }

    private void syncStatusToCloud(String requestedState) {
        String uid = sessionManager.getOwnerUid();
        String devId = sessionManager.getCurrentDeviceId();
        if (uid == null || devId == null) return;

        com.example.securetrack.utils.ProtectionHealthEvaluator.ProtectionReport report =
                com.example.securetrack.utils.ProtectionHealthEvaluator.evaluate(this);

        String effectiveTrackingStatus;
        if ("STOPPED".equalsIgnoreCase(requestedState) || !sessionManager.isBackgroundTrackingEnabled()) {
            effectiveTrackingStatus = "STOPPED";
        } else if (isLocationUpdatesActive && report.isLocationServiceEnabled && report.isLocationPermGranted) {
            effectiveTrackingStatus = "ACTIVE";
        } else {
            effectiveTrackingStatus = "PAUSED";
        }

        Map<String, Object> update = new HashMap<>();
        update.put("trackingStatus", effectiveTrackingStatus);
        update.put("lastSeen", FieldValue.serverTimestamp());
        update.put("locationStatus", report.isLocationServiceEnabled ? "ENABLED" : "DISABLED");
        update.put("locationPermission", report.isLocationPermGranted ? "GRANTED" : "DENIED");
        update.put("bluetoothStatus", report.isBluetoothEnabled ? "ENABLED" : "DISABLED");
        update.put("bluetoothPermission", report.isBluetoothPermGranted ? "GRANTED" : "DENIED");
        update.put("protectionStatus", report.status);
        update.put("protectionReason", report.reason);
        update.put("protectionEnabled", report.protectionEnabled && "ACTIVE".equals(effectiveTrackingStatus));
        update.put("status", report.isConnected ? "ONLINE" : "OFFLINE");
        update.put("networkStatus", report.isConnected ? "CONNECTED" : "DISCONNECTED");

        if (report.isConnected) {
            FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("devices").document(devId)
                    .set(update, SetOptions.merge())
                    .addOnFailureListener(e -> Log.w(TAG, "Failed syncing tracking status: " + e.getMessage()));
        } else {
            Log.i(TAG, "Device offline; telemetry status update queued locally for reconnection");
        }
    }

    public void stopProtectionService() {
        isServiceRunning = false;
        sessionManager.setBackgroundTrackingEnabled(false);
        pauseLocationUpdates();

        if (commandExecutionManager != null) {
            commandExecutionManager.stopListeningToCommands();
        }

        syncStatusToCloud("STOPPED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        Log.i(TAG, "LocationTrackingService onDestroy");

        pauseLocationUpdates();

        if (commandExecutionManager != null) {
            commandExecutionManager.stopListeningToCommands();
        }

        if (locationProviderReceiver != null) {
            try {
                unregisterReceiver(locationProviderReceiver);
            } catch (Exception ignored) {}
        }

        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
            } catch (Exception ignored) {}
        }

        syncStatusToCloud("STOPPED");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
