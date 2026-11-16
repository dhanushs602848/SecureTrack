package com.example.securetrack.location;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.LocationDao;
import com.example.securetrack.database.LocationEntity;
import com.example.securetrack.models.LocationRecord;
import com.example.securetrack.repositories.LocationRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LocationSyncWorker extends Worker {

    private final LocationDao locationDao;
    private final LocationRepository locationRepository;

    public LocationSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.locationDao = AppDatabase.getInstance(context).locationDao();
        this.locationRepository = new LocationRepository(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<LocationEntity> unsynced = locationDao.getUnsyncedLocations();
        if (unsynced == null || unsynced.isEmpty()) {
            return Result.success();
        }

        boolean allSucceeded = true;
        for (LocationEntity entity : unsynced) {
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] successFlag = {false};

            LocationRecord record = new LocationRecord(
                    entity.getDeviceId(),
                    entity.getClientUuid(),
                    entity.getLatitude(),
                    entity.getLongitude(),
                    entity.getAccuracy(),
                    entity.getAltitude(),
                    entity.getSpeed(),
                    entity.getTimestamp()
            );

            locationRepository.recordLocation(record, new LocationRepository.ApiCallback<LocationRecord>() {
                @Override
                public void onSuccess(LocationRecord result) {
                    successFlag[0] = true;
                    locationDao.markAsSynced(entity.getId());
                    latch.countDown();
                }

                @Override
                public void onError(String errorMessage) {
                    successFlag[0] = false;
                    latch.countDown();
                }
            });

            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                allSucceeded = false;
            }

            if (!successFlag[0]) {
                allSucceeded = false;
            }
        }

        locationDao.deleteSyncedLocations();

        return allSucceeded ? Result.success() : Result.retry();
    }
}
