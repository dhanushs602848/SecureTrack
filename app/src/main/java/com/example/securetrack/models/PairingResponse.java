package com.example.securetrack.models;

public class PairingResponse {
    private String code;
    private String expiresAt;
    private Device device;

    public String getCode() { return code; }
    public String getExpiresAt() { return expiresAt; }
    public Device getDevice() { return device; }
}
