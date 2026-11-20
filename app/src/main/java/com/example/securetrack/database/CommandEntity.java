package com.example.securetrack.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "durable_commands")
public class CommandEntity {

    // Standard state machine states
    public static final String STATE_QUEUED_LOCAL = "QUEUED_LOCAL";
    public static final String STATE_SENT_CLOUD = "SENT_CLOUD";
    public static final String STATE_SENT_BLE = "SENT_BLE";
    public static final String STATE_RECEIVED = "RECEIVED";
    public static final String STATE_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATE_EXECUTING = "EXECUTING";
    public static final String STATE_EXECUTED = "EXECUTED";
    public static final String STATE_SYNC_PENDING = "SYNC_PENDING";
    public static final String STATE_SYNCED = "SYNCED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_EXPIRED = "EXPIRED";

    // Transports
    public static final String TRANSPORT_CLOUD = "CLOUD";
    public static final String TRANSPORT_BLE = "BLE";
    public static final String TRANSPORT_LOCAL = "LOCAL";

    @PrimaryKey
    @NonNull
    private String commandId;
    private String targetDeviceId;
    private String commandType;
    private String requestingUid;
    private String payload;
    private String status;
    private String result;
    private String transport;
    private long createdAt;
    private long expiresAt;
    private long acknowledgedAt;
    private long executedAt;
    private String syncStatus;
    private int retryCount;

    public CommandEntity(@NonNull String commandId, String targetDeviceId, String commandType,
                         String requestingUid, String payload, String status, String result,
                         String transport, long createdAt, long expiresAt, long acknowledgedAt,
                         long executedAt, String syncStatus, int retryCount) {
        this.commandId = commandId;
        this.targetDeviceId = targetDeviceId;
        this.commandType = commandType;
        this.requestingUid = requestingUid;
        this.payload = payload;
        this.status = status;
        this.result = result;
        this.transport = transport;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.acknowledgedAt = acknowledgedAt;
        this.executedAt = executedAt;
        this.syncStatus = syncStatus;
        this.retryCount = retryCount;
    }

    @NonNull
    public String getCommandId() { return commandId; }
    public void setCommandId(@NonNull String commandId) { this.commandId = commandId; }
    public String getTargetDeviceId() { return targetDeviceId; }
    public void setTargetDeviceId(String targetDeviceId) { this.targetDeviceId = targetDeviceId; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
    public String getRequestingUid() { return requestingUid; }
    public void setRequestingUid(String requestingUid) { this.requestingUid = requestingUid; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getTransport() { return transport; }
    public void setTransport(String transport) { this.transport = transport; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public long getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(long acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public long getExecutedAt() { return executedAt; }
    public void setExecutedAt(long executedAt) { this.executedAt = executedAt; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }
}
