package com.example.securetrack.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface LocationDao {

    @Insert
    long insert(LocationEntity location);

    @Query("SELECT * FROM offline_locations WHERE clientUuid = :clientUuid LIMIT 1")
    LocationEntity getByClientUuid(String clientUuid);

    @Query("SELECT * FROM offline_locations WHERE isSynced = 0 ORDER BY timestamp ASC")
    List<LocationEntity> getUnsyncedLocations();

    @Query("UPDATE offline_locations SET isSynced = 1 WHERE id = :id")
    void markAsSynced(long id);

    @Query("DELETE FROM offline_locations WHERE isSynced = 1")
    void deleteSyncedLocations();

    @Query("DELETE FROM offline_locations")
    void deleteAll();
}
