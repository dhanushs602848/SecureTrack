package com.example.securetrack.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_devices")
public class DeviceEntity {

    @PrimaryKey
    @NonNull
    private String deviceId;
    private String ownerUid;
    private String deviceName;
    private String model;
    private String manufacturer;
    private int batteryLevel;
    private boolean isOnline;
    private String status;
    private String lastSeen;
    private double lastLatitude;
    private double lastLongitude;
    private float lastAccuracy;
    private long lastLocationTime;
    private String locationProvider;
    private boolean isLostMode;
    private long syncTimestamp;

    public DeviceEntity(@NonNull String deviceId, String ownerUid, String deviceName, String model,
                        String manufacturer, int batteryLevel, boolean isOnline, String status,
                        String lastSeen, double lastLatitude, double lastLongitude, float lastAccuracy,
                        long lastLocationTime, String locationProvider, boolean isLostMode, long syncTimestamp) {
        this.deviceId = deviceId;
        this.ownerUid = ownerUid;
        this.deviceName = deviceName;
        this.model = model;
        this.manufacturer = manufacturer;
        this.batteryLevel = batteryLevel;
        this.isOnline = isOnline;
        this.status = status;
        this.lastSeen = lastSeen;
        this.lastLatitude = lastLatitude;
        this.lastLongitude = lastLongitude;
        this.lastAccuracy = lastAccuracy;
        this.lastLocationTime = lastLocationTime;
        this.locationProvider = locationProvider;
        this.isLostMode = isLostMode;
        this.syncTimestamp = syncTimestamp;
    }

    @NonNull
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(@NonNull String deviceId) { this.deviceId = deviceId; }
    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public int getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
    public double getLastLatitude() { return lastLatitude; }
    public void setLastLatitude(double lastLatitude) { this.lastLatitude = lastLatitude; }
    public double getLastLongitude() { return lastLongitude; }
    public void setLastLongitude(double lastLongitude) { this.lastLongitude = lastLongitude; }
    public float getLastAccuracy() { return lastAccuracy; }
    public void setLastAccuracy(float lastAccuracy) { this.lastAccuracy = lastAccuracy; }
    public long getLastLocationTime() { return lastLocationTime; }
    public void setLastLocationTime(long lastLocationTime) { this.lastLocationTime = lastLocationTime; }
    public String getLocationProvider() { return locationProvider; }
    public void setLocationProvider(String locationProvider) { this.locationProvider = locationProvider; }
    public boolean isLostMode() { return isLostMode; }
    public void setLostMode(boolean lostMode) { isLostMode = lostMode; }
    public long getSyncTimestamp() { return syncTimestamp; }
    public void setSyncTimestamp(long syncTimestamp) { this.syncTimestamp = syncTimestamp; }
}
