package com.example.securetrack.sync;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.database.SecurityAlertEntity;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import com.example.securetrack.utils.SessionManager;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Durable offline-to-online synchronizer running via WorkManager.
 * Synchronizes:
 * - Offline GPS location points
 * - Offline alert dismissals and resolutions
 * - Discards/expires stale security commands (preventing delayed execution of old lock/alarm)
 */
public class DurableSyncWorker extends Worker {

    private static final String TAG = "DurableSyncWorker";

    private final AppDatabase db;
    private final FirebaseFirestore firestore;
    private final SessionManager sessionManager;
    private final LocationRepository locationRepository;

    public DurableSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.db = AppDatabase.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.sessionManager = new SessionManager(context);
        this.locationRepository = new LocationRepository(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Starting durable synchronization cycle");

        String uid = sessionManager.getOwnerUid();
        String deviceId = sessionManager.getCurrentDeviceId();

        if (uid == null || deviceId == null) {
            Log.w(TAG, "Sync skipped: No authenticated session");
            return Result.success();
        }

        // Ensure remote command listener is active on this device
        com.example.securetrack.security.CommandExecutionManager.getInstance(getApplicationContext()).startListeningToCommands();

        try {
            // 1. Expire stale pending commands locally
            int expired = db.commandDao().expireStaleCommands(System.currentTimeMillis());
            if (expired > 0) {
                Log.i(TAG, "Expired " + expired + " stale pending commands locally");
            }

            // 2. Sync unsynced offline location records
            List<LocationEntity> unsyncedLocs = db.locationDao().getUnsyncedLocations();
            if (unsyncedLocs != null && !unsyncedLocs.isEmpty()) {
                Log.i(TAG, "Uploading " + unsyncedLocs.size() + " offline location fixes");
                for (LocationEntity loc : unsyncedLocs) {
                    LocationRecord record = new LocationRecord(
                            loc.getDeviceId(),
                            loc.getClientUuid(),
                            loc.getLatitude(),
                            loc.getLongitude(),
                            loc.getAccuracy(),
                            loc.getAltitude(),
                            loc.getSpeed(),
                            loc.getTimestamp()
                    );
                    try {
                        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                        final boolean[] uploadSuccess = new boolean[]{false};
                        locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
                            @Override
                            public void onSuccess(LocationRecord result) {
                                uploadSuccess[0] = true;
                                latch.countDown();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                uploadSuccess[0] = false;
                                latch.countDown();
                            }
                        });
                        boolean completed = latch.await(10, TimeUnit.SECONDS);
                        if (completed && uploadSuccess[0]) {
                            db.locationDao().markAsSynced(loc.getId());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed syncing location: " + loc.getClientUuid(), e);
                    }
                }
                db.locationDao().deleteSyncedLocations();
            }

            // 3. Sync unsynced alert updates (dismissals / resolutions) to Firestore
            List<SecurityAlertEntity> unsyncedAlerts = db.securityAlertDao().getUnsyncedAlerts();
            if (unsyncedAlerts != null && !unsyncedAlerts.isEmpty()) {
                Log.i(TAG, "Syncing " + unsyncedAlerts.size() + " alert state changes to cloud");
                for (SecurityAlertEntity alert : unsyncedAlerts) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", alert.getStatus());
                    if (alert.getDismissedAt() > 0) {
                        updates.put("dismissedAt", alert.getDismissedAt());
                        updates.put("dismissedBy", alert.getDismissedBy());
                    }
                    if (alert.getResolvedAt() > 0) {
                        updates.put("resolvedAt", alert.getResolvedAt());
                    }

                    try {
                        Tasks.await(
                                firestore.collection("users").document(uid)
                                        .collection("alerts").document(alert.getId())
                                        .set(updates, SetOptions.merge()),
                                5, TimeUnit.SECONDS
                        );
                        db.securityAlertDao().markAlertSynced(alert.getId());
                    } catch (Exception e) {
                        Log.w(TAG, "Alert state sync delayed for " + alert.getId() + ": " + e.getMessage());
                    }
                }
            }

            Log.i(TAG, "Durable synchronization cycle completed successfully");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error during durable synchronization", e);
            return Result.retry();
        }
    }
}
