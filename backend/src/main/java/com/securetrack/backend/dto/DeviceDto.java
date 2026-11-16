package com.securetrack.backend.dto;

import java.time.LocalDateTime;

public class DeviceDto {
    private Long id;
    private Long userId;
    private String deviceName;
    private String model;
    private String androidVersion;
    private String installationId;
    private String status;
    private Boolean protectionEnabled;
    private Integer batteryLevel;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    public DeviceDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

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
    public void setProtectionEnabled(Boolean protectionEnabled) { this.protectionEnabled = protectionEnabled; }

    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
