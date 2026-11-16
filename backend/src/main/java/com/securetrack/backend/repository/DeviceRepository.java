package com.securetrack.backend.repository;

import com.securetrack.backend.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {
    List<DeviceEntity> findByUserId(Long userId);
    Optional<DeviceEntity> findByInstallationId(String installationId);
    Optional<DeviceEntity> findByIdAndUserId(Long id, Long userId);
}
