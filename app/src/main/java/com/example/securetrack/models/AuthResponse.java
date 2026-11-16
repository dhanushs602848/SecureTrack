package com.example.securetrack.models;

public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private User user;

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public User getUser() { return user; }
}
