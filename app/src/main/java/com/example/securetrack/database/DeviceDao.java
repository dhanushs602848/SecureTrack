package com.example.securetrack.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface DeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DeviceEntity device);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DeviceEntity> devices);

    @Query("SELECT * FROM cached_devices WHERE ownerUid = :ownerUid ORDER BY syncTimestamp DESC")
    LiveData<List<DeviceEntity>> getDevicesLiveData(String ownerUid);

    @Query("SELECT * FROM cached_devices WHERE ownerUid = :ownerUid ORDER BY syncTimestamp DESC")
    List<DeviceEntity> getDevicesSync(String ownerUid);

    @Query("SELECT * FROM cached_devices WHERE deviceId = :deviceId LIMIT 1")
    DeviceEntity getDeviceById(String deviceId);

    @Query("SELECT * FROM cached_devices WHERE deviceId = :deviceId LIMIT 1")
    LiveData<DeviceEntity> getDeviceByIdLiveData(String deviceId);

    @Query("UPDATE cached_devices SET isLostMode = :isLost WHERE deviceId = :deviceId")
    void setLostMode(String deviceId, boolean isLost);

    @Query("UPDATE cached_devices SET lastLatitude = :lat, lastLongitude = :lng, lastAccuracy = :acc, lastLocationTime = :time, locationProvider = :provider WHERE deviceId = :deviceId")
    void updateLocation(String deviceId, double lat, double lng, float acc, long time, String provider);

    @Query("DELETE FROM cached_devices WHERE deviceId = :deviceId")
    void deleteDevice(String deviceId);

    @Query("DELETE FROM cached_devices")
    void clearAll();
}
