package com.securetrack.backend.repository;

import com.securetrack.backend.entity.PairingCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PairingCodeRepository extends JpaRepository<PairingCodeEntity, Long> {
    Optional<PairingCodeEntity> findByCodeAndUsedFalseAndExpiresAtAfter(String code, LocalDateTime now);
    Optional<PairingCodeEntity> findByCode(String code);
}
