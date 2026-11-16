package com.securetrack.backend.service;

import com.securetrack.backend.dto.DeviceDto;
import com.securetrack.backend.dto.PairingRequest;
import com.securetrack.backend.dto.PairingResponse;
import com.securetrack.backend.entity.DeviceEntity;
import com.securetrack.backend.entity.PairingCodeEntity;
import com.securetrack.backend.entity.UserEntity;
import com.securetrack.backend.exception.BadRequestException;
import com.securetrack.backend.exception.ResourceNotFoundException;
import com.securetrack.backend.repository.DeviceRepository;
import com.securetrack.backend.repository.PairingCodeRepository;
import com.securetrack.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class PairingService {

    private final PairingCodeRepository pairingCodeRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final SecureRandom random = new SecureRandom();

    public PairingService(PairingCodeRepository pairingCodeRepository,
                          UserRepository userRepository,
                          DeviceRepository deviceRepository,
                          DeviceService deviceService) {
        this.pairingCodeRepository = pairingCodeRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
    }

    @Transactional
    public PairingResponse createPairingCode(Long userId) {
        UserEntity owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String code;
        do {
            code = String.format("%06d", random.nextInt(1000000));
        } while (pairingCodeRepository.findByCode(code).isPresent());

        PairingCodeEntity pairingCode = new PairingCodeEntity();
        pairingCode.setCode(code);
        pairingCode.setOwnerUser(owner);
        pairingCode.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        pairingCode.setUsed(false);

        pairingCodeRepository.save(pairingCode);

        return new PairingResponse(code, pairingCode.getExpiresAt(), null);
    }

    @Transactional
    public DeviceDto completePairing(PairingRequest request) {
        PairingCodeEntity pairingCode = pairingCodeRepository
                .findByCodeAndUsedFalseAndExpiresAtAfter(request.getCode(), LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired pairing code"));

        UserEntity owner = pairingCode.getOwnerUser();

        DeviceDto dto = new DeviceDto();
        dto.setDeviceName(request.getDeviceName());
        dto.setModel(request.getModel());
        dto.setAndroidVersion(request.getAndroidVersion());
        dto.setInstallationId(request.getInstallationId());

        DeviceDto pairedDevice = deviceService.registerDevice(owner.getId(), dto);

        pairingCode.setUsed(true);
        DeviceEntity deviceEntity = deviceRepository.findById(pairedDevice.getId()).orElse(null);
        pairingCode.setTargetDevice(deviceEntity);
        pairingCodeRepository.save(pairingCode);

        return pairedDevice;
    }
}
