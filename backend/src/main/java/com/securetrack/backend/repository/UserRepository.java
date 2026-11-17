package com.securetrack.backend.repository;

import com.securetrack.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByGoogleSubId(String googleSubId);
    boolean existsByEmail(String email);
}
