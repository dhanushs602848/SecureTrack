package com.securetrack.backend.repository;

import com.securetrack.backend.entity.DeviceCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommandEntity, Long> {
    List<DeviceCommandEntity> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);
    List<DeviceCommandEntity> findByDeviceIdAndStatus(Long deviceId, String status);
    Optional<DeviceCommandEntity> findByIdAndDeviceId(Long id, Long deviceId);
}
