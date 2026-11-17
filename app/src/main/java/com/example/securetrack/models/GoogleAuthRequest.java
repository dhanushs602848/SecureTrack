package com.example.securetrack.models;

public class GoogleAuthRequest {
    private String idToken;

    public GoogleAuthRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() { return idToken; }
}
