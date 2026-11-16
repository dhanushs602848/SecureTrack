package com.securetrack.backend.controller;

import com.securetrack.backend.dto.ApiResponse;
import com.securetrack.backend.dto.SecurityEventDto;
import com.securetrack.backend.security.UserPrincipal;
import com.securetrack.backend.service.SecurityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/security-events")
public class SecurityController {

    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SecurityEventDto>> recordSecurityEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SecurityEventDto dto) {
        SecurityEventDto event = securityService.recordSecurityEvent(principal.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success("Security event recorded", event));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SecurityEventDto>>> getUserSecurityEvents(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SecurityEventDto> events = securityService.getUserSecurityEvents(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Security events retrieved", events));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<List<SecurityEventDto>>> getDeviceSecurityEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("deviceId") Long deviceId) {
        List<SecurityEventDto> events = securityService.getDeviceSecurityEvents(principal.getId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("Device security events retrieved", events));
    }
}
