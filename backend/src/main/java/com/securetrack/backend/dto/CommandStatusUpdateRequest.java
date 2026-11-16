package com.securetrack.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CommandStatusUpdateRequest {
    @NotBlank(message = "Status is required")
    private String status;

    private String result;

    public CommandStatusUpdateRequest() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
