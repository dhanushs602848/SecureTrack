package com.example.securetrack.models;

public class LocationRecord {
    private Long id;
    private Long deviceId;
    private String clientUuid;
    private Double latitude;
    private Double longitude;
    private Float accuracy;
    private Double altitude;
    private Float speed;
    private String timestamp;

    public LocationRecord() {}

    public LocationRecord(Long deviceId, String clientUuid, Double latitude, Double longitude, Float accuracy, Double altitude, Float speed, String timestamp) {
        this.deviceId = deviceId;
        this.clientUuid = clientUuid;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public String getClientUuid() { return clientUuid; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Float getAccuracy() { return accuracy; }
    public Double getAltitude() { return altitude; }
    public Float getSpeed() { return speed; }
    public String getTimestamp() { return timestamp; }
}
