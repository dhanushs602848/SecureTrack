package com.securetrack.backend.controller;

import com.securetrack.backend.dto.ApiResponse;
import com.securetrack.backend.dto.DeviceDto;
import com.securetrack.backend.security.UserPrincipal;
import com.securetrack.backend.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceDto>> registerDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeviceDto dto) {
        DeviceDto device = deviceService.registerDevice(principal.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success("Device registered successfully", device));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceDto>>> getUserDevices(@AuthenticationPrincipal UserPrincipal principal) {
        List<DeviceDto> devices = deviceService.getUserDevices(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Devices retrieved successfully", devices));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceDto>> getDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id) {
        DeviceDto device = deviceService.getDevice(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Device details retrieved", device));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceDto>> updateDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id,
            @RequestBody DeviceDto dto) {
        DeviceDto device = deviceService.updateDevice(principal.getId(), id, dto);
        return ResponseEntity.ok(ApiResponse.success("Device updated successfully", device));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long id) {
        deviceService.deleteDevice(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Device deleted successfully", "Deleted"));
    }
}
