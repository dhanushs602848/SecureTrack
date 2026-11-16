package com.securetrack.backend.service;

import com.securetrack.backend.dto.LocationDto;
import com.securetrack.backend.entity.DeviceEntity;
import com.securetrack.backend.entity.LocationEntity;
import com.securetrack.backend.exception.ResourceNotFoundException;
import com.securetrack.backend.exception.UnauthorizedException;
import com.securetrack.backend.repository.DeviceRepository;
import com.securetrack.backend.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final DeviceRepository deviceRepository;

    public LocationService(LocationRepository locationRepository, DeviceRepository deviceRepository) {
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public LocationDto recordLocation(Long userId, LocationDto dto) {
        DeviceEntity device = deviceRepository.findById(dto.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (!device.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("User does not own this device");
        }

        // Idempotency check with clientUuid
        Optional<LocationEntity> existing = locationRepository.findByClientUuid(dto.getClientUuid());
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        LocationEntity location = new LocationEntity();
        location.setDevice(device);
        location.setClientUuid(dto.getClientUuid());
        location.setLatitude(dto.getLatitude());
        location.setLongitude(dto.getLongitude());
        location.setAccuracy(dto.getAccuracy() != null ? dto.getAccuracy() : 0.0f);
        location.setAltitude(dto.getAltitude() != null ? dto.getAltitude() : 0.0);
        location.setSpeed(dto.getSpeed() != null ? dto.getSpeed() : 0.0f);
        location.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now());

        location = locationRepository.save(location);

        // Update device last seen
        device.setLastSeen(LocalDateTime.now());
        device.setStatus("ONLINE");
        deviceRepository.save(device);

        return toDto(location);
    }

    @Transactional(readOnly = true)
    public LocationDto getLatestLocation(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        LocationEntity location = locationRepository.findFirstByDeviceIdOrderByTimestampDesc(device.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No location history available for device"));

        return toDto(location);
    }

    @Transactional(readOnly = true)
    public List<LocationDto> getLocationHistory(Long userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found or not owned by user"));

        return locationRepository.findByDeviceIdOrderByTimestampDesc(device.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public LocationDto toDto(LocationEntity entity) {
        LocationDto dto = new LocationDto();
        dto.setId(entity.getId());
        dto.setDeviceId(entity.getDevice().getId());
        dto.setClientUuid(entity.getClientUuid());
        dto.setLatitude(entity.getLatitude());
        dto.setLongitude(entity.getLongitude());
        dto.setAccuracy(entity.getAccuracy());
        dto.setAltitude(entity.getAltitude());
        dto.setSpeed(entity.getSpeed());
        dto.setTimestamp(entity.getTimestamp());
        return dto;
    }
}
