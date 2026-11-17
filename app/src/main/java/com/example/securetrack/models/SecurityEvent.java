package com.example.securetrack.models;

public class SecurityEvent {
    private String id;
    private String deviceId;
    private String eventType;
    private String severity;
    private String description;
    private String timestamp;

    public SecurityEvent() {}

    public SecurityEvent(String deviceId, String eventType, String severity, String description) {
        this.deviceId = deviceId;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
