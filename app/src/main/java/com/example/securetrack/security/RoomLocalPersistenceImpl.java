package com.example.securetrack.security;

import android.content.Context;
import com.example.securetrack.database.AppDatabase;
import com.example.securetrack.database.CommandEntity;
import com.example.securetrack.database.LocationEntity;
import java.util.List;

public class RoomLocalPersistenceImpl implements ILocalPersistence {
    private final AppDatabase db;

    public RoomLocalPersistenceImpl(Context context) {
        this.db = AppDatabase.getInstance(context);
    }

    public RoomLocalPersistenceImpl(AppDatabase db) {
        this.db = db;
    }

    @Override
    public LocationEntity getLocationByClientUuid(String clientUuid) {
        return db.locationDao().getByClientUuid(clientUuid);
    }

    @Override
    public long insertLocation(LocationEntity entity) {
        return db.locationDao().insert(entity);
    }

    @Override
    public void markLocationSynced(long id) {
        db.locationDao().markAsSynced(id);
    }

    @Override
    public CommandEntity getCommandById(String commandId) {
        return db.commandDao().getCommandById(commandId);
    }

    @Override
    public void insertOrUpdateCommand(CommandEntity command) {
        db.commandDao().insertOrUpdate(command);
    }

    @Override
    public void updateCommandExecution(String commandId, String status, String result, long executedAt, String syncStatus) {
        db.commandDao().updateExecutionStatus(commandId, status, result, executedAt);
        if (syncStatus != null) {
            db.commandDao().updateSyncStatus(commandId, syncStatus);
        }
    }

    @Override
    public void updateCommandSyncStatus(String commandId, String syncStatus) {
        db.commandDao().updateSyncStatus(commandId, syncStatus);
    }

    @Override
    public List<CommandEntity> getPendingSyncCommands() {
        return db.commandDao().getPendingSyncCommands();
    }
}
