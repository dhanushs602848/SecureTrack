package com.example.securetrack.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LocationEntity location);

    @Query("SELECT * FROM offline_locations WHERE synced = 0 ORDER BY id ASC")
    List<LocationEntity> getUnsyncedLocations();

    @Query("UPDATE offline_locations SET synced = 1 WHERE id = :id")
    void markAsSynced(long id);

    @Query("DELETE FROM offline_locations WHERE synced = 1")
    void deleteSyncedLocations();
}
