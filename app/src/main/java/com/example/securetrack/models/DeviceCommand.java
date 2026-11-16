package com.example.securetrack.models;

public class DeviceCommand {
    private Long id;
    private Long deviceId;
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
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public String getCommandType() { return commandType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public String getResult() { return result; }
    public String getCreatedAt() { return createdAt; }
    public String getExecutedAt() { return executedAt; }
}
