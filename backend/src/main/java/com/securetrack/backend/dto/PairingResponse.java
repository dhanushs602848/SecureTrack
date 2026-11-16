package com.securetrack.backend.dto;

import java.time.LocalDateTime;

public class PairingResponse {
    private String code;
    private LocalDateTime expiresAt;
    private DeviceDto device;

    public PairingResponse() {}

    public PairingResponse(String code, LocalDateTime expiresAt, DeviceDto device) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.device = device;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public DeviceDto getDevice() { return device; }
    public void setDevice(DeviceDto device) { this.device = device; }
}
