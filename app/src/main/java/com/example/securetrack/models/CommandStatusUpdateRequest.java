package com.example.securetrack.models;

public class CommandStatusUpdateRequest {
    private String status;
    private String result;

    public CommandStatusUpdateRequest(String status, String result) {
        this.status = status;
        this.result = result;
    }

    public String getStatus() { return status; }
    public String getResult() { return result; }
}
