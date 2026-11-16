package com.securetrack.backend.service;

import com.securetrack.backend.dto.CommandDto;
import com.securetrack.backend.dto.CommandStatusUpdateRequest;
import com.securetrack.backend.entity.DeviceCommandEntity;
import com.securetrack.backend.entity.DeviceEntity;
import com.securetrack.backend.exception.ResourceNotFoundException;
import com.securetrack.backend.exception.UnauthorizedException;
import com.securetrack.backend.repository.DeviceCommandRepository;
import com.securetrack.backend.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CommandService {

    private final DeviceCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;

    public CommandService(DeviceCommandRepository commandRepository, DeviceRepository deviceRepository) {
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public CommandDto createCommand(Long userId, Long deviceId, CommandDto dto) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        DeviceCommandEntity command = new DeviceCommandEntity();
        command.setDevice(device);
        command.setCommandType(dto.getCommandType());
        command.setPayload(dto.getPayload());
        command.setStatus("QUEUED");

        command = commandRepository.save(command);
        return toDto(command);
    }

    @Transactional(readOnly = true)
    public List<CommandDto> getDeviceCommands(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        return commandRepository.findByDeviceIdOrderByCreatedAtDesc(device.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<CommandDto> pollPendingCommands(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        List<DeviceCommandEntity> pending = commandRepository.findByDeviceIdAndStatus(device.getId(), "QUEUED");
        for (DeviceCommandEntity cmd : pending) {
            cmd.setStatus("DELIVERED");
            commandRepository.save(cmd);
        }

        return pending.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public CommandDto updateCommandStatus(Long userId, Long commandId, CommandStatusUpdateRequest request) {
        DeviceCommandEntity command = commandRepository.findById(commandId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found"));

        if (!command.getDevice().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("User does not own the target device for this command");
        }

        command.setStatus(request.getStatus());
        if (request.getResult() != null) {
            command.setResult(request.getResult());
        }
        if ("EXECUTED".equals(request.getStatus()) || "FAILED".equals(request.getStatus())) {
            command.setExecutedAt(LocalDateTime.now());
        }

        command = commandRepository.save(command);
        return toDto(command);
    }

    public CommandDto toDto(DeviceCommandEntity entity) {
        CommandDto dto = new CommandDto();
        dto.setId(entity.getId());
        dto.setDeviceId(entity.getDevice().getId());
        dto.setCommandType(entity.getCommandType());
        dto.setPayload(entity.getPayload());
        dto.setStatus(entity.getStatus());
        dto.setResult(entity.getResult());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setExecutedAt(entity.getExecutedAt());
        return dto;
    }
}
