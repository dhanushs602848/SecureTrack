package com.securetrack.backend.repository;

import com.securetrack.backend.entity.SecurityEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityEventRepository extends JpaRepository<SecurityEventEntity, Long> {
    List<SecurityEventEntity> findByDeviceIdOrderByTimestampDesc(Long deviceId);
    List<SecurityEventEntity> findByDeviceUserIdOrderByTimestampDesc(Long userId);
}
