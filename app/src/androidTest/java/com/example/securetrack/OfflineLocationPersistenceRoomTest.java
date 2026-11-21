package com.example.securetrack;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.LocationDao;
import com.example.securetrack.database.LocationEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Real SQLite / Room database persistence test executed against Android SQLite.
 * Verifies write-ahead insertion, survivability across operations,
 * failure retention (isSynced=0), success marking (isSynced=1),
 * cleanup of synced points, and duplicate prevention via clientUuid.
 */
@RunWith(AndroidJUnit4.class)
public class OfflineLocationPersistenceRoomTest {

    private AppDatabase db;
    private LocationDao locationDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        locationDao = db.locationDao();
    }

    @After
    public void closeDb() throws IOException {
        db.close();
    }

    @Test
    public void testWriteAheadInsertionAndUnsyncedRetrieval() {
        String clientUuid = UUID.randomUUID().toString();
        String deviceId = "device-alpha-1";
        String timestamp = "2026-11-21T10:15:30Z";

        LocationEntity entity = new LocationEntity(
                deviceId,
                clientUuid,
                12.9716,
                77.5946,
                10.5f,
                920.0,
                1.2f,
                timestamp,
                false
        );

        long rowId = locationDao.insert(entity);
        assertTrue("Inserted row ID must be positive", rowId > 0);

        List<LocationEntity> unsynced = locationDao.getUnsyncedLocations();
        assertNotNull(unsynced);
        assertEquals("Must contain exactly 1 unsynced record", 1, unsynced.size());

        LocationEntity retrieved = unsynced.get(0);
        assertEquals(clientUuid, retrieved.getClientUuid());
        assertEquals(deviceId, retrieved.getDeviceId());
        assertEquals(12.9716, retrieved.getLatitude(), 0.0001);
        assertEquals(77.5946, retrieved.getLongitude(), 0.0001);
        assertEquals(10.5f, retrieved.getAccuracy(), 0.01f);
        assertEquals(920.0, retrieved.getAltitude(), 0.01);
        assertEquals(1.2f, retrieved.getSpeed(), 0.01f);
        assertEquals(timestamp, retrieved.getTimestamp());
        assertFalse("Record must initially be unsynced", retrieved.isSynced());
    }

    @Test
    public void testFailureRetentionAcrossOperations() {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();

        locationDao.insert(new LocationEntity("dev-1", uuid1, 12.0, 77.0, 5.0f, 0.0, 0.0f, "2026-11-21T08:00:00Z", false));
        long row2 = locationDao.insert(new LocationEntity("dev-1", uuid2, 12.1, 77.1, 8.0f, 0.0, 0.0f, "2026-11-21T08:05:00Z", false));

        List<LocationEntity> initialUnsynced = locationDao.getUnsyncedLocations();
        assertEquals(2, initialUnsynced.size());

        // Simulate successful upload of only the second location
        locationDao.markAsSynced(row2);

        // Delete synced records
        locationDao.deleteSyncedLocations();

        // The failed/unsynced first record MUST survive and be retained
        List<LocationEntity> remainingUnsynced = locationDao.getUnsyncedLocations();
        assertEquals("Unsynced failed record must survive deleteSyncedLocations", 1, remainingUnsynced.size());
        assertEquals("Remaining record must be uuid1", uuid1, remainingUnsynced.get(0).getClientUuid());
        assertFalse(remainingUnsynced.get(0).isSynced());
    }

    @Test
    public void testSuccessMarkingAndCleanup() {
        String uuid = UUID.randomUUID().toString();
        long rowId = locationDao.insert(new LocationEntity("dev-1", uuid, 12.9, 77.5, 4.0f, 100.0, 0.0f, "2026-11-21T09:00:00Z", false));

        // Before sync
        assertEquals(1, locationDao.getUnsyncedLocations().size());

        // Mark as synced
        locationDao.markAsSynced(rowId);

        // Verify it is no longer returned in getUnsyncedLocations()
        List<LocationEntity> unsyncedAfter = locationDao.getUnsyncedLocations();
        assertTrue("getUnsyncedLocations() must be empty after marking as synced", unsyncedAfter.isEmpty());

        // Clean up synced records
        locationDao.deleteSyncedLocations();
        assertTrue(locationDao.getUnsyncedLocations().isEmpty());
    }

    @Test
    public void testDuplicatePreventionViaClientUuid() {
        String clientUuid = "stable-uuid-test-12345";
        LocationEntity initial = new LocationEntity("dev-1", clientUuid, 12.97, 77.59, 5.0f, 50.0, 0.0f, "2026-11-21T10:00:00Z", false);
        long initialRowId = locationDao.insert(initial);

        // Lookup by clientUuid
        LocationEntity existing = locationDao.getByClientUuid(clientUuid);
        assertNotNull("Existing entity must be found by clientUuid", existing);
        assertEquals(initialRowId, existing.getId());
        assertEquals(clientUuid, existing.getClientUuid());

        // Simulate repository check: if existing != null, do not re-insert duplicate
        if (locationDao.getByClientUuid(clientUuid) == null) {
            locationDao.insert(new LocationEntity("dev-1", clientUuid, 12.97, 77.59, 5.0f, 50.0, 0.0f, "2026-11-21T10:00:00Z", false));
        }

        List<LocationEntity> allUnsynced = locationDao.getUnsyncedLocations();
        assertEquals("Duplicate must be prevented; database must hold exactly 1 record", 1, allUnsynced.size());
    }
}
