package com.example.securetrack.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface CommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(CommandEntity command);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CommandEntity> commands);

    @Update
    void update(CommandEntity command);

    @Query("SELECT * FROM durable_commands WHERE targetDeviceId = :deviceId ORDER BY createdAt DESC")
    LiveData<List<CommandEntity>> getCommandsForDeviceLiveData(String deviceId);

    @Query("SELECT * FROM durable_commands WHERE targetDeviceId = :deviceId ORDER BY createdAt DESC")
    List<CommandEntity> getCommandsForDeviceSync(String deviceId);

    @Query("SELECT * FROM durable_commands WHERE commandId = :commandId LIMIT 1")
    CommandEntity getCommandById(String commandId);

    @Query("SELECT * FROM durable_commands WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    List<CommandEntity> getPendingSyncCommands();

    @Query("UPDATE durable_commands SET status = :status, result = :result, executedAt = :executedAt WHERE commandId = :commandId")
    void updateExecutionStatus(String commandId, String status, String result, long executedAt);

    @Query("UPDATE durable_commands SET syncStatus = :syncStatus WHERE commandId = :commandId")
    void updateSyncStatus(String commandId, String syncStatus);

    @Query("UPDATE durable_commands SET status = 'EXPIRED', result = 'Command expired locally before delivery' WHERE expiresAt > 0 AND expiresAt < :currentTime AND status IN ('QUEUED_LOCAL', 'SYNC_PENDING')")
    int expireStaleCommands(long currentTime);

    @Query("DELETE FROM durable_commands WHERE commandId = :commandId")
    void deleteCommand(String commandId);

    @Query("DELETE FROM durable_commands WHERE createdAt < :beforeTimestamp")
    void purgeOldCommands(long beforeTimestamp);
}
