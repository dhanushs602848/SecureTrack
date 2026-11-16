package com.example.securetrack.models;

public class Device {
    private Long id;
    private Long userId;
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
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDeviceName() { return deviceName; }
    public String getModel() { return model; }
    public String getAndroidVersion() { return androidVersion; }
    public String getInstallationId() { return installationId; }
    public String getStatus() { return status; }
    public Boolean getProtectionEnabled() { return protectionEnabled; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public String getLastSeen() { return lastSeen; }
}
