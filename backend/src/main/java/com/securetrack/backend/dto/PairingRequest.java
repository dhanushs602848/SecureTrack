package com.securetrack.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class PairingRequest {
    @NotBlank(message = "Pairing code is required")
    private String code;

    @NotBlank(message = "Installation ID is required")
    private String installationId;

    @NotBlank(message = "Device name is required")
    private String deviceName;

    private String model;
    private String androidVersion;

    public PairingRequest() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }
}
