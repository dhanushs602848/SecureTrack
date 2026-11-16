package com.example.securetrack.models;

public class PairingRequest {
    private String code;
    private String installationId;
    private String deviceName;
    private String model;
    private String androidVersion;

    public PairingRequest(String code, String installationId, String deviceName, String model, String androidVersion) {
        this.code = code;
        this.installationId = installationId;
        this.deviceName = deviceName;
        this.model = model;
        this.androidVersion = androidVersion;
    }

    public String getCode() { return code; }
    public String getInstallationId() { return installationId; }
    public String getDeviceName() { return deviceName; }
    public String getModel() { return model; }
    public String getAndroidVersion() { return androidVersion; }
}
