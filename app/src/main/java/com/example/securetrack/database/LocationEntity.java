package com.example.securetrack.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_locations")
public class LocationEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long deviceId;
    private String clientUuid;
    private double latitude;
    private double longitude;
    private float accuracy;
    private double altitude;
    private float speed;
    private String timestamp;
    private boolean synced;

    public LocationEntity(long deviceId, String clientUuid, double latitude, double longitude, float accuracy, double altitude, float speed, String timestamp, boolean synced) {
        this.deviceId = deviceId;
        this.clientUuid = clientUuid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.timestamp = timestamp;
        this.synced = synced;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDeviceId() { return deviceId; }
    public String getClientUuid() { return clientUuid; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getAccuracy() { return accuracy; }
    public double getAltitude() { return altitude; }
    public float getSpeed() { return speed; }
    public String getTimestamp() { return timestamp; }
    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }
}
