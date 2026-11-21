package com.example.securetrack.security;

import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.LocationEntity;
import java.util.List;

public interface ILocalPersistence {
    LocationEntity getLocationByClientUuid(String clientUuid);
    long insertLocation(LocationEntity entity);
    void markLocationSynced(long id);
    CommandEntity getCommandById(String commandId);
    void insertOrUpdateCommand(CommandEntity command);
    void updateCommandExecution(String commandId, String status, String result, long executedAt, String syncStatus);
    void updateCommandSyncStatus(String commandId, String syncStatus);
    List<CommandEntity> getPendingSyncCommands();
}
