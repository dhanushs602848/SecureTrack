package com.example.securetrack.models;

public class SecurityEvent {
    private Long id;
    private Long deviceId;
    private String eventType;
    private String severity;
    private String description;
    private String timestamp;

    public SecurityEvent() {}

    public SecurityEvent(Long deviceId, String eventType, String severity, String description) {
        this.deviceId = deviceId;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public String getEventType() { return eventType; }
    public String getSeverity() { return severity; }
    public String getDescription() { return description; }
    public String getTimestamp() { return timestamp; }
}
