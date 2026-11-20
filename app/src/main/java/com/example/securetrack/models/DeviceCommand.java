package com.example.securetrack.models;

public class DeviceCommand {
    private String id;
    private String deviceId;
    private String commandType;
    private String payload;
    private String status;
    private String result;
    private String createdAt;
    private String executedAt;
    private String transport = "CLOUD";

    public DeviceCommand() {}

    public DeviceCommand(String deviceId, String commandType, String payload) {
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

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

    public String getTransport() { return transport != null ? transport : "CLOUD"; }
    public void setTransport(String transport) { this.transport = transport; }
}
