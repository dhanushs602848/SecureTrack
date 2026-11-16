package com.example.securetrack.models;

public class PasswordResetRequest {
    private String email;

    public PasswordResetRequest(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
}
