package com.securetrack.backend.controller;

import com.securetrack.backend.dto.ApiResponse;
import com.securetrack.backend.dto.DeviceDto;
import com.securetrack.backend.dto.PairingRequest;
import com.securetrack.backend.dto.PairingResponse;
import com.securetrack.backend.security.UserPrincipal;
import com.securetrack.backend.service.PairingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pairing")
public class PairingController {

    private final PairingService pairingService;

    public PairingController(PairingService pairingService) {
        this.pairingService = pairingService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PairingResponse>> createPairingCode(@AuthenticationPrincipal UserPrincipal principal) {
        PairingResponse response = pairingService.createPairingCode(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Pairing code created", response));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<DeviceDto>> completePairing(@Valid @RequestBody PairingRequest request) {
        DeviceDto device = pairingService.completePairing(request);
        return ResponseEntity.ok(ApiResponse.success("Device paired successfully", device));
    }
}
