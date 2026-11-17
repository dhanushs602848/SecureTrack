package com.example.securetrack.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_locations")
public class LocationEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String deviceId;
    private String clientUuid;
    private double latitude;
    private double longitude;
    private float accuracy;
    private double altitude;
    private float speed;
    private String timestamp;
    private boolean isSynced;

    public LocationEntity(String deviceId, String clientUuid, double latitude, double longitude, float accuracy, double altitude, float speed, String timestamp, boolean isSynced) {
        this.deviceId = deviceId;
        this.clientUuid = clientUuid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.timestamp = timestamp;
        this.isSynced = isSynced;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getClientUuid() { return clientUuid; }
    public void setClientUuid(String clientUuid) { this.clientUuid = clientUuid; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }
}
