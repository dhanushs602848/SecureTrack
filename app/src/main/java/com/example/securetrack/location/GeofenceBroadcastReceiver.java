package com.example.securetrack.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            String errorMessage = GeofenceStatusCodes.getStatusCodeString(
                    geofencingEvent != null ? geofencingEvent.getErrorCode() : GeofenceStatusCodes.ERROR);
            Log.e(TAG, errorMessage);
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            if (triggeringGeofences == null || triggeringGeofences.isEmpty()) return;

            String transitionType = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ?
                    "SAFE_ZONE_ENTER" : "SAFE_ZONE_EXIT";

            String severity = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ?
                    "INFO" : "WARNING";

            SessionManager sessionManager = new SessionManager(context.getApplicationContext());
            String deviceId = sessionManager.getPrimaryDeviceIdStr();
            if (deviceId == null) return;

            SecurityRepository repo = new SecurityRepository(context.getApplicationContext());

            for (Geofence geofence : triggeringGeofences) {
                String zoneId = geofence.getRequestId();
                String desc = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ?
                        "Device entered safe zone " + zoneId :
                        "Device exited safe zone " + zoneId;

                SecurityEvent event = new SecurityEvent(deviceId, transitionType, severity, desc);
                repo.recordSecurityEvent(event, new SecurityRepository.ApiCallback<SecurityEvent>() {
                    @Override
                    public void onSuccess(SecurityEvent result) {
                        Log.i(TAG, "Geofence event logged: " + transitionType);
                    }
                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Failed to log geofence event: " + errorMessage);
                    }
                });
            }
        }
    }
}
