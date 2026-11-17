package com.example.securetrack.models;

public class LocationRecord {
    private String id;
    private String deviceId;
    private String clientUuid;
    private Double latitude;
    private Double longitude;
    private Float accuracy;
    private Double altitude;
    private Float speed;
    private String timestamp;

    public LocationRecord() {}

    public LocationRecord(String deviceId, String clientUuid, Double latitude, Double longitude, Float accuracy, Double altitude, Float speed, String timestamp) {
        this.deviceId = deviceId;
        this.clientUuid = clientUuid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getClientUuid() { return clientUuid; }
    public void setClientUuid(String clientUuid) { this.clientUuid = clientUuid; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Float getAccuracy() { return accuracy; }
    public void setAccuracy(Float accuracy) { this.accuracy = accuracy; }
    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }
    public Float getSpeed() { return speed; }
    public void setSpeed(Float speed) { this.speed = speed; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
