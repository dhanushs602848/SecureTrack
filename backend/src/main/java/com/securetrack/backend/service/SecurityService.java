package com.securetrack.backend.service;

import com.securetrack.backend.dto.SecurityEventDto;
import com.securetrack.backend.entity.DeviceEntity;
import com.securetrack.backend.entity.SecurityEventEntity;
import com.securetrack.backend.exception.ResourceNotFoundException;
import com.securetrack.backend.exception.UnauthorizedException;
import com.securetrack.backend.repository.DeviceRepository;
import com.securetrack.backend.repository.SecurityEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SecurityService {

    private final SecurityEventRepository securityEventRepository;
    private final DeviceRepository deviceRepository;

    public SecurityService(SecurityEventRepository securityEventRepository, DeviceRepository deviceRepository) {
        this.securityEventRepository = securityEventRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public SecurityEventDto recordSecurityEvent(Long userId, SecurityEventDto dto) {
        DeviceEntity device = deviceRepository.findById(dto.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (!device.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("User does not own this device");
        }

        SecurityEventEntity event = new SecurityEventEntity();
        event.setDevice(device);
        event.setEventType(dto.getEventType());
        event.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : "INFO");
        event.setDescription(dto.getDescription());
        event.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());

        event = securityEventRepository.save(event);
        return toDto(event);
    }

    @Transactional(readOnly = true)
    public List<SecurityEventDto> getUserSecurityEvents(Long userId) {
        return securityEventRepository.findByDeviceUserIdOrderByTimestampDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SecurityEventDto> getDeviceSecurityEvents(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        return securityEventRepository.findByDeviceIdOrderByTimestampDesc(device.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public SecurityEventDto toDto(SecurityEventEntity entity) {
        SecurityEventDto dto = new SecurityEventDto();
        dto.setId(entity.getId());
        dto.setDeviceId(entity.getDevice().getId());
        dto.setEventType(entity.getEventType());
        dto.setSeverity(entity.getSeverity());
        dto.setDescription(entity.getDescription());
        dto.setTimestamp(entity.getTimestamp());
        return dto;
    }
}
