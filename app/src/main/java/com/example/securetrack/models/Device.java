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
    private Integer batteryLevel;
    private String lastSeen;

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

    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
}
