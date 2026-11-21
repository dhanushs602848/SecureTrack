package com.example.securetrack.models;

public class Device {
    private String id;
    private String userId;
    private String deviceName;
    private String model;
    private String androidVersion;
    private String installationId;
    private String status;
    private Boolean protectionEnabled;
    private String protectionStatus;
    private String protectionReason;
    private Integer batteryLevel;
    private String lastSeen;
    private String imei1;
    private String imei2;
    private Double lastLatitude;
    private Double lastLongitude;
    private Float locationAccuracy;
    private com.google.firebase.Timestamp locationTimestamp;
    private String locationStatus;
    private String trackingStatus;
    private String locationPermission;
    private String ownerUid;

    public Device() {}

    public Device(String deviceName, String model, String androidVersion, String installationId, Integer batteryLevel) {
        this.deviceName = deviceName;
        this.model = model;
        this.androidVersion = androidVersion;
        this.installationId = installationId;
        this.batteryLevel = batteryLevel;
        this.protectionEnabled = true;
        this.status = "ONLINE";
    }

    public Device(String id, String deviceName, String model, String androidVersion, Integer batteryLevel, String status, Boolean protectionEnabled, String installationId) {
        this.id = id;
        this.deviceName = deviceName;
        this.model = model;
        this.androidVersion = androidVersion;
        this.batteryLevel = batteryLevel;
        this.status = status;
        this.protectionEnabled = protectionEnabled;
        this.installationId = installationId;
    }

    public String getId() { return id != null ? id : installationId; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }

    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getProtectionEnabled() { return protectionEnabled; }
    public boolean isProtectionEnabled() { return Boolean.TRUE.equals(protectionEnabled); }
    public void setProtectionEnabled(Boolean protectionEnabled) { this.protectionEnabled = protectionEnabled; }

    public String getProtectionStatus() { return protectionStatus; }
    public void setProtectionStatus(String protectionStatus) { this.protectionStatus = protectionStatus; }

    public String getProtectionReason() { return protectionReason; }
    public void setProtectionReason(String protectionReason) { this.protectionReason = protectionReason; }

    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }

    public String getImei1() { return imei1; }
    public void setImei1(String imei1) { this.imei1 = imei1; }

    public String getImei2() { return imei2; }
    public void setImei2(String imei2) { this.imei2 = imei2; }

    public Double getLastLatitude() { return lastLatitude; }
    public void setLastLatitude(Double lastLatitude) { this.lastLatitude = lastLatitude; }

    public Double getLastLongitude() { return lastLongitude; }
    public void setLastLongitude(Double lastLongitude) { this.lastLongitude = lastLongitude; }

    public Float getLocationAccuracy() { return locationAccuracy; }
    public void setLocationAccuracy(Float locationAccuracy) { this.locationAccuracy = locationAccuracy; }

    public com.google.firebase.Timestamp getLocationTimestamp() { return locationTimestamp; }
    public void setLocationTimestamp(com.google.firebase.Timestamp locationTimestamp) { this.locationTimestamp = locationTimestamp; }

    public String getLocationStatus() { return locationStatus; }
    public void setLocationStatus(String locationStatus) { this.locationStatus = locationStatus; }

    public String getTrackingStatus() { return trackingStatus; }
    public void setTrackingStatus(String trackingStatus) { this.trackingStatus = trackingStatus; }

    public String getLocationPermission() { return locationPermission; }
    public void setLocationPermission(String locationPermission) { this.locationPermission = locationPermission; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public boolean isOnline() {
        if ("OFFLINE".equalsIgnoreCase(status) || "REVOKED".equalsIgnoreCase(status)) {
            return false;
        }
        if (locationTimestamp != null) {
            long age = System.currentTimeMillis() - locationTimestamp.toDate().getTime();
            return age < 15 * 60 * 1000L; // 15 mins threshold
        }
        return "ONLINE".equalsIgnoreCase(status);
    }

    public boolean isLocationStale() {
        if (lastLatitude == null || lastLongitude == null) return true;
        if (locationTimestamp == null) return true;
        long age = System.currentTimeMillis() - locationTimestamp.toDate().getTime();
        return age > 15 * 60 * 1000L; // older than 15 mins
    }

    public String getFormattedLastSeen() {
        if (locationTimestamp != null) {
            long diffMs = System.currentTimeMillis() - locationTimestamp.toDate().getTime();
            if (diffMs < 0) diffMs = 0;
            long mins = diffMs / (60 * 1000L);
            if (mins < 1) return "Just now";
            if (mins < 60) return mins + "m ago";
            long hours = mins / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            return days + "d ago";
        }
        return lastSeen != null && !lastSeen.isEmpty() ? lastSeen : "Never";
    }

    public String getFormattedAccuracy() {
        if (locationAccuracy != null && locationAccuracy > 0) {
            return String.format(java.util.Locale.US, "±%.0fm", locationAccuracy);
        }
        return "N/A";
    }
}
