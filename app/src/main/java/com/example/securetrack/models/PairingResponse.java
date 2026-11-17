package com.example.securetrack.models;

public class PairingResponse {
    private String code;
    private String expiresAt;
    private Device device;

    public PairingResponse() {}

    public PairingResponse(String code, String expiresAt) {
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public PairingResponse(String code, int ttlSeconds) {
        this.code = code;
        this.expiresAt = String.valueOf(System.currentTimeMillis() + (ttlSeconds * 1000L));
    }

    public String getCode() { return code; }
    public String getExpiresAt() { return expiresAt; }
    public Device getDevice() { return device; }
}
