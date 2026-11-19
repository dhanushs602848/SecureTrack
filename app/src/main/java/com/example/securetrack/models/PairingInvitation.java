package com.example.securetrack.models;

import java.util.Date;
import java.util.Map;

public class PairingInvitation {
    private String rawToken;
    private String tokenHash;
    private String ownerUid;
    private String ownerEmail;
    private String status;
    private Date expiresAt;
    private String claimedByUid;
    private String claimedDeviceId;
    private Map<String, Object> deviceInfo;
    private String qrPayload;

    public PairingInvitation() {}

    public PairingInvitation(String rawToken, String tokenHash, String ownerUid, String ownerEmail, String status, Date expiresAt, String qrPayload) {
        this.rawToken = rawToken;
        this.tokenHash = tokenHash;
        this.ownerUid = ownerUid;
        this.ownerEmail = ownerEmail;
        this.status = status;
        this.expiresAt = expiresAt;
        this.qrPayload = qrPayload;
    }

    public String getRawToken() {
        return rawToken;
    }

    public void setRawToken(String rawToken) {
        this.rawToken = rawToken;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(String ownerUid) {
        this.ownerUid = ownerUid;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getClaimedByUid() {
        return claimedByUid;
    }

    public void setClaimedByUid(String claimedByUid) {
        this.claimedByUid = claimedByUid;
    }

    public String getClaimedDeviceId() {
        return claimedDeviceId;
    }

    public void setClaimedDeviceId(String claimedDeviceId) {
        this.claimedDeviceId = claimedDeviceId;
    }

    public Map<String, Object> getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(Map<String, Object> deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }
}
