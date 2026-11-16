package com.securetrack.backend.repository;

import com.securetrack.backend.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {
    Optional<LocationEntity> findFirstByDeviceIdOrderByTimestampDesc(Long deviceId);
    List<LocationEntity> findByDeviceIdOrderByTimestampDesc(Long deviceId);
    Optional<LocationEntity> findByClientUuid(String clientUuid);
    boolean existsByClientUuid(String clientUuid);
}
