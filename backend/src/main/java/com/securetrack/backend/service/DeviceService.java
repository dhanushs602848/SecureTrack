package com.securetrack.backend.service;

import com.securetrack.backend.dto.DeviceDto;
import com.securetrack.backend.entity.DeviceEntity;
import com.securetrack.backend.entity.UserEntity;
import com.securetrack.backend.exception.ResourceNotFoundException;
import com.securetrack.backend.exception.UnauthorizedException;
import com.securetrack.backend.repository.DeviceRepository;
import com.securetrack.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public DeviceService(DeviceRepository deviceRepository, UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DeviceDto registerDevice(Long userId, DeviceDto dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Optional<DeviceEntity> existing = deviceRepository.findByInstallationId(dto.getInstallationId());
        DeviceEntity device;

        if (existing.isPresent()) {
            device = existing.get();
            if (!device.getUser().getId().equals(userId)) {
                throw new UnauthorizedException("Device installation ID is already linked to another account");
            }
            device.setDeviceName(dto.getDeviceName());
            device.setModel(dto.getModel());
            device.setAndroidVersion(dto.getAndroidVersion());
            device.setLastSeen(LocalDateTime.now());
            device.setStatus("ONLINE");
        } else {
            device = new DeviceEntity();
            device.setUser(user);
            device.setDeviceName(dto.getDeviceName());
            device.setModel(dto.getModel());
            device.setAndroidVersion(dto.getAndroidVersion());
            device.setInstallationId(dto.getInstallationId());
            device.setStatus("ONLINE");
            device.setProtectionEnabled(true);
            device.setBatteryLevel(dto.getBatteryLevel() != null ? dto.getBatteryLevel() : 100);
            device.setLastSeen(LocalDateTime.now());
        }

        device = deviceRepository.save(device);
        return toDto(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceDto> getUserDevices(Long userId) {
        return deviceRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeviceDto getDevice(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));
        return toDto(device);
    }

    @Transactional
    public DeviceDto updateDevice(Long userId, Long deviceId, DeviceDto dto) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        if (dto.getDeviceName() != null) device.setDeviceName(dto.getDeviceName());
        if (dto.getProtectionEnabled() != null) device.setProtectionEnabled(dto.getProtectionEnabled());
        if (dto.getBatteryLevel() != null) device.setBatteryLevel(dto.getBatteryLevel());
        if (dto.getStatus() != null) device.setStatus(dto.getStatus());
        device.setLastSeen(LocalDateTime.now());

        device = deviceRepository.save(device);
        return toDto(device);
    }

    @Transactional
    public void deleteDevice(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));
        deviceRepository.delete(device);
    }

    public DeviceDto toDto(DeviceEntity entity) {
        DeviceDto dto = new DeviceDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUser().getId());
        dto.setDeviceName(entity.getDeviceName());
        dto.setModel(entity.getModel());
        dto.setAndroidVersion(entity.getAndroidVersion());
        dto.setInstallationId(entity.getInstallationId());
        dto.setStatus(entity.getStatus());
        dto.setProtectionEnabled(entity.getProtectionEnabled());
        dto.setBatteryLevel(entity.getBatteryLevel());
        dto.setLastSeen(entity.getLastSeen());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
