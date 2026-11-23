package com.example.securetrack.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;
import com.example.securetrack.models.SecurityEvent;
import com.example.securetrack.repositories.SecurityRepository;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust Geofence Event Handler for SecureTrack.
 *
 * Responsibilities:
 * 1. Validates geofence transitions (EXIT vs ENTER).
 * 2. On EXIT: Logs security event, transitions device to OUT_OF_ZONE, and enables geofence Lost Mode.
 * 3. On ENTER: Logs security event, transitions device to IN_ZONE, and clears Lost Mode
 *    strictly if it was triggered by geofencing (never overriding manual Lost Mode).
 * 4. Captures triggering location coordinates if available in the GeofencingEvent.
 */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            String errorMessage = GeofenceStatusCodes.getStatusCodeString(
                    geofencingEvent != null ? geofencingEvent.getErrorCode() : GeofenceStatusCodes.ERROR);
            Log.e(TAG, "Geofencing error: " + errorMessage);
            return;
        }

        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            if (triggeringGeofences == null || triggeringGeofences.isEmpty()) return;

            boolean isExit = (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT);
            String transitionType = isExit ? "GEOFENCE_EXIT" : "GEOFENCE_ENTER";
            String severity = isExit ? "WARNING" : "INFO";

            Context appContext = context.getApplicationContext();
            SessionManager sessionManager = new SessionManager(appContext);
            String deviceId = sessionManager.getCurrentDeviceId();
            String ownerUid = sessionManager.getOwnerUid();
            if (deviceId == null || ownerUid == null) {
                Log.w(TAG, "Missing deviceId or ownerUid; cannot update geofence state in Firestore");
                return;
            }

            Location triggeringLocation = geofencingEvent.getTriggeringLocation();
            SecurityRepository repo = new SecurityRepository(appContext);

            for (Geofence geofence : triggeringGeofences) {
                String zoneId = geofence.getRequestId();
                String desc;
                if (isExit) {
                    desc = "Device exited safe zone: " + zoneId + ". Automatic out-of-zone protection engaged.";
                    sessionManager.setGeofenceLostMode(true);
                } else {
                    desc = "Device entered safe zone: " + zoneId + ". Safe zone confirmed.";
                }

                SecurityEvent event = new SecurityEvent(deviceId, transitionType, severity, desc);
                repo.recordSecurityEvent(event, new SecurityRepository.ApiCallback<SecurityEvent>() {
                    @Override
                    public void onSuccess(SecurityEvent result) {
                        Log.i(TAG, "Geofence event logged to security center: " + transitionType);
                    }
                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Failed to log geofence event: " + errorMessage);
                    }
                });

                // Update device document zone status and lost mode in Firestore
                try {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("zoneStatus", isExit ? "OUT_OF_ZONE" : "IN_ZONE");
                    updates.put("lastZoneId", zoneId);
                    updates.put("lastZoneTransition", transitionType);
                    updates.put("lastZoneTransitionTime", FieldValue.serverTimestamp());

                    if (isExit) {
                        updates.put("isLostMode", true);
                        updates.put("lostModeSource", "GEOFENCE");
                    } else {
                        // On ENTER: restore lost mode ONLY if it was triggered by geofence
                        if (sessionManager.isGeofenceLostMode()) {
                            updates.put("isLostMode", false);
                            updates.put("lostModeSource", "RECOVERED_IN_ZONE");
                            sessionManager.setGeofenceLostMode(false);
                            Log.i(TAG, "Restored geofence-triggered Lost Mode upon safe zone re-entry");
                        } else {
                            Log.i(TAG, "Preserving manual Lost Mode state upon safe zone re-entry");
                        }
                    }

                    if (triggeringLocation != null) {
                        updates.put("lastLatitude", triggeringLocation.getLatitude());
                        updates.put("lastLongitude", triggeringLocation.getLongitude());
                        updates.put("lastAccuracy", triggeringLocation.getAccuracy());
                        updates.put("locationTimestamp", FieldValue.serverTimestamp());
                    }

                    FirebaseFirestore.getInstance()
                            .collection("users").document(ownerUid)
                            .collection("devices").document(deviceId)
                            .set(updates, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> Log.i(TAG, "Updated device zone status: " + (isExit ? "OUT_OF_ZONE" : "IN_ZONE")))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update device zone status in Firestore", e));
                } catch (Exception e) {
                    Log.e(TAG, "Exception updating Firestore zone state", e);
                }
            }
        }
    }
}
