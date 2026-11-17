package com.example.securetrack.models;

public class DeviceCommand {
    private Long id;
    private String idStr;
    private Long deviceId;
    private String deviceIdStr;
    private String commandType;
    private String payload;
    private String status;
    private String result;
    private String createdAt;
    private String executedAt;

    public DeviceCommand() {}

    public DeviceCommand(Long deviceId, String commandType, String payload) {
        this.deviceId = deviceId;
        this.commandType = commandType;
        this.payload = payload;
        this.status = "QUEUED";
    }

    public DeviceCommand(String commandType, String status, String payload, String result) {
        this.commandType = commandType;
        this.status = status;
        this.payload = payload;
        this.result = result;
    }

    public DeviceCommand(String idStr, String deviceIdStr, String commandType, String status, String payload, String result) {
        this.idStr = idStr;
        try { this.id = Long.parseLong(idStr); } catch (Exception ignored) {}
        this.deviceIdStr = deviceIdStr;
        this.commandType = commandType;
        this.status = status;
        this.payload = payload;
        this.result = result;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdStr() { return idStr != null ? idStr : (id != null ? String.valueOf(id) : null); }
    public void setIdStr(String idStr) { this.idStr = idStr; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceIdStr() { return deviceIdStr != null ? deviceIdStr : (deviceId != null ? String.valueOf(deviceId) : null); }
    public void setDeviceIdStr(String deviceIdStr) { this.deviceIdStr = deviceIdStr; }

    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getExecutedAt() { return executedAt; }
    public void setExecutedAt(String executedAt) { this.executedAt = executedAt; }
}
