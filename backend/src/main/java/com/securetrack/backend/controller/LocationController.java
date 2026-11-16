package com.securetrack.backend.controller;

import com.securetrack.backend.dto.ApiResponse;
import com.securetrack.backend.dto.LocationDto;
import com.securetrack.backend.security.UserPrincipal;
import com.securetrack.backend.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LocationDto>> recordLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LocationDto dto) {
        LocationDto location = locationService.recordLocation(principal.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success("Location recorded successfully", location));
    }

    @GetMapping("/latest/{deviceId}")
    public ResponseEntity<ApiResponse<LocationDto>> getLatestLocation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("deviceId") Long deviceId) {
        LocationDto location = locationService.getLatestLocation(principal.getId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("Latest location retrieved", location));
    }

    @GetMapping("/history/{deviceId}")
    public ResponseEntity<ApiResponse<List<LocationDto>>> getLocationHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("deviceId") Long deviceId) {
        List<LocationDto> history = locationService.getLocationHistory(principal.getId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("Location history retrieved", history));
    }
}
