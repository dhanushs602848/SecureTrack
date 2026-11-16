package com.securetrack.backend.controller;

import com.securetrack.backend.dto.ApiResponse;
import com.securetrack.backend.dto.CommandDto;
import com.securetrack.backend.dto.CommandStatusUpdateRequest;
import com.securetrack.backend.security.UserPrincipal;
import com.securetrack.backend.service.CommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/devices/{id}/commands")
    public ResponseEntity<ApiResponse<CommandDto>> createCommand(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long deviceId,
            @Valid @RequestBody CommandDto dto) {
        CommandDto command = commandService.createCommand(principal.getId(), deviceId, dto);
        return ResponseEntity.ok(ApiResponse.success("Command dispatched", command));
    }

    @GetMapping("/devices/{id}/commands")
    public ResponseEntity<ApiResponse<List<CommandDto>>> getDeviceCommands(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long deviceId) {
        List<CommandDto> commands = commandService.getDeviceCommands(principal.getId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("Device command history retrieved", commands));
    }

    @GetMapping("/devices/{id}/commands/pending")
    public ResponseEntity<ApiResponse<List<CommandDto>>> pollPendingCommands(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") Long deviceId) {
        List<CommandDto> pending = commandService.pollPendingCommands(principal.getId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success("Pending commands retrieved", pending));
    }

    @PutMapping("/commands/{commandId}/status")
    public ResponseEntity<ApiResponse<CommandDto>> updateCommandStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("commandId") Long commandId,
            @Valid @RequestBody CommandStatusUpdateRequest request) {
        CommandDto updated = commandService.updateCommandStatus(principal.getId(), commandId, request);
        return ResponseEntity.ok(ApiResponse.success("Command status updated", updated));
    }
}
