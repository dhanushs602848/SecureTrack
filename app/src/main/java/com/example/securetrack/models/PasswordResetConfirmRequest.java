package com.example.securetrack.models;

public class PasswordResetConfirmRequest {
    private String token;
    private String newPassword;

    public PasswordResetConfirmRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() { return token; }
    public String getNewPassword() { return newPassword; }
}
