package com.example.securetrack.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {
        LocationEntity.class,
        DeviceEntity.class,
        CommandEntity.class,
        SecurityAlertEntity.class
}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final java.util.concurrent.ExecutorService databaseWriteExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract LocationDao locationDao();
    public abstract DeviceDao deviceDao();
    public abstract CommandDao commandDao();
    public abstract SecurityAlertDao securityAlertDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `cached_devices` (" +
                    "`deviceId` TEXT NOT NULL PRIMARY KEY, " +
                    "`ownerUid` TEXT, " +
                    "`deviceName` TEXT, " +
                    "`model` TEXT, " +
                    "`manufacturer` TEXT, " +
                    "`batteryLevel` INTEGER NOT NULL, " +
                    "`isOnline` INTEGER NOT NULL, " +
                    "`status` TEXT, " +
                    "`lastSeen` TEXT, " +
                    "`lastLatitude` REAL NOT NULL, " +
                    "`lastLongitude` REAL NOT NULL, " +
                    "`lastAccuracy` REAL NOT NULL, " +
                    "`lastLocationTime` INTEGER NOT NULL, " +
                    "`locationProvider` TEXT, " +
                    "`isLostMode` INTEGER NOT NULL, " +
                    "`syncTimestamp` INTEGER NOT NULL)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `durable_commands` (" +
                    "`commandId` TEXT NOT NULL PRIMARY KEY, " +
                    "`targetDeviceId` TEXT, " +
                    "`commandType` TEXT, " +
                    "`requestingUid` TEXT, " +
                    "`payload` TEXT, " +
                    "`status` TEXT, " +
                    "`result` TEXT, " +
                    "`transport` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`expiresAt` INTEGER NOT NULL, " +
                    "`acknowledgedAt` INTEGER NOT NULL, " +
                    "`executedAt` INTEGER NOT NULL, " +
                    "`syncStatus` TEXT, " +
                    "`retryCount` INTEGER NOT NULL)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `security_alerts` (" +
                    "`id` TEXT NOT NULL PRIMARY KEY, " +
                    "`deviceId` TEXT, " +
                    "`deviceName` TEXT, " +
                    "`eventType` TEXT, " +
                    "`severity` TEXT, " +
                    "`description` TEXT, " +
                    "`locationStatus` TEXT, " +
                    "`status` TEXT, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`dismissedAt` INTEGER NOT NULL, " +
                    "`dismissedBy` TEXT, " +
                    "`resolvedAt` INTEGER NOT NULL, " +
                    "`isSynced` INTEGER NOT NULL)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "securetrack_db")
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
