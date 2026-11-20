package com.example.securetrack.models;

public class SecurityEvent {
    private String id;
    private String deviceId;
    private String deviceName;
    private String eventType;
    private String severity; // INFO, WARNING, CRITICAL
    private String description;
    private String timestamp;
    private String locationStatus;
    private String status = "ACTIVE"; // ACTIVE, ACKNOWLEDGED, RESOLVED, DISMISSED, ARCHIVED
    private long dismissedAt;
    private String dismissedBy;
    private long resolvedAt;

    public SecurityEvent() {}

    public SecurityEvent(String deviceId, String eventType, String severity, String description) {
        this.deviceId = deviceId;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
    }

    public SecurityEvent(String deviceId, String deviceName, String eventType, String severity, String description, String locationStatus) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
        this.locationStatus = locationStatus;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getLocationStatus() { return locationStatus; }
    public void setLocationStatus(String locationStatus) { this.locationStatus = locationStatus; }

    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status; }
    public long getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(long dismissedAt) { this.dismissedAt = dismissedAt; }
    public String getDismissedBy() { return dismissedBy; }
    public void setDismissedBy(String dismissedBy) { this.dismissedBy = dismissedBy; }
    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }

    public boolean isDismissed() {
        return "DISMISSED".equalsIgnoreCase(status);
    }

    public boolean isResolved() {
        return "RESOLVED".equalsIgnoreCase(status);
    }
}
