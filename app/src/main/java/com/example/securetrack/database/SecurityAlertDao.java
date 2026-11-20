package com.example.securetrack.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface SecurityAlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(SecurityAlertEntity alert);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SecurityAlertEntity> alerts);

    @Update
    void update(SecurityAlertEntity alert);

    @Query("SELECT * FROM security_alerts WHERE status != 'DISMISSED' AND status != 'ARCHIVED' ORDER BY timestamp DESC")
    LiveData<List<SecurityAlertEntity>> getActiveAlertsLiveData();

    @Query("SELECT * FROM security_alerts WHERE status != 'DISMISSED' AND status != 'ARCHIVED' ORDER BY timestamp DESC")
    List<SecurityAlertEntity> getActiveAlertsSync();

    @Query("SELECT * FROM security_alerts ORDER BY timestamp DESC")
    LiveData<List<SecurityAlertEntity>> getAllAlertsLiveData();

    @Query("SELECT * FROM security_alerts WHERE status = :status ORDER BY timestamp DESC")
    LiveData<List<SecurityAlertEntity>> getAlertsByStatusLiveData(String status);

    @Query("SELECT * FROM security_alerts WHERE id = :alertId LIMIT 1")
    SecurityAlertEntity getAlertById(String alertId);

    @Query("UPDATE security_alerts SET status = 'DISMISSED', dismissedAt = :time, dismissedBy = :byUid, isSynced = 0 WHERE id = :alertId")
    void dismissAlert(String alertId, long time, String byUid);

    @Query("UPDATE security_alerts SET status = 'ACTIVE', dismissedAt = 0, dismissedBy = null, isSynced = 0 WHERE id = :alertId")
    void restoreAlert(String alertId);

    @Query("UPDATE security_alerts SET status = 'RESOLVED', resolvedAt = :time, isSynced = 0 WHERE id = :alertId")
    void resolveAlert(String alertId, long time);

    @Query("UPDATE security_alerts SET status = 'DISMISSED', dismissedAt = :time, dismissedBy = :byUid, isSynced = 0 WHERE (status = 'RESOLVED' OR severity = 'INFO') AND status != 'DISMISSED'")
    int bulkDismissResolved(long time, String byUid);

    @Query("SELECT COUNT(*) FROM security_alerts WHERE status = 'ACTIVE' AND severity = 'CRITICAL'")
    LiveData<Integer> getCriticalActiveAlertCountLiveData();

    @Query("SELECT * FROM security_alerts WHERE isSynced = 0")
    List<SecurityAlertEntity> getUnsyncedAlerts();

    @Query("UPDATE security_alerts SET isSynced = 1 WHERE id = :alertId")
    void markAlertSynced(String alertId);

    @Query("DELETE FROM security_alerts WHERE id = :alertId")
    void deleteAlert(String alertId);
}
