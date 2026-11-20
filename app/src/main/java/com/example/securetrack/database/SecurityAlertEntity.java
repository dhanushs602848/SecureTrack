package com.example.securetrack.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "security_alerts")
public class SecurityAlertEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    @PrimaryKey
    @NonNull
    private String id;
    private String deviceId;
    private String deviceName;
    private String eventType;
    private String severity;
    private String description;
    private String locationStatus;
    private String status;
    private long timestamp;
    private long dismissedAt;
    private String dismissedBy;
    private long resolvedAt;
    private boolean isSynced;

    public SecurityAlertEntity(@NonNull String id, String deviceId, String deviceName,
                               String eventType, String severity, String description,
                               String locationStatus, String status, long timestamp,
                               long dismissedAt, String dismissedBy, long resolvedAt, boolean isSynced) {
        this.id = id;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.eventType = eventType;
        this.severity = severity;
        this.description = description;
        this.locationStatus = locationStatus;
        this.status = status;
        this.timestamp = timestamp;
        this.dismissedAt = dismissedAt;
        this.dismissedBy = dismissedBy;
        this.resolvedAt = resolvedAt;
        this.isSynced = isSynced;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
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
    public String getLocationStatus() { return locationStatus; }
    public void setLocationStatus(String locationStatus) { this.locationStatus = locationStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(long dismissedAt) { this.dismissedAt = dismissedAt; }
    public String getDismissedBy() { return dismissedBy; }
    public void setDismissedBy(String dismissedBy) { this.dismissedBy = dismissedBy; }
    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }
    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }

    public boolean isDismissed() {
        return STATUS_DISMISSED.equalsIgnoreCase(status);
    }

    public boolean isResolved() {
        return STATUS_RESOLVED.equalsIgnoreCase(status);
    }
}
