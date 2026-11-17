package com.securetrack.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.securetrack.backend.dto.*;
import com.securetrack.backend.entity.*;
import com.securetrack.backend.exception.BadRequestException;
import com.securetrack.backend.exception.ResourceNotFoundException;
import com.securetrack.backend.repository.*;
import com.securetrack.backend.security.JwtTokenProvider;
import com.securetrack.backend.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final MailService mailService;

    @Value("${google.client-id:unconfigured-google-client-id}")
    private String googleClientId;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuthenticationManager authenticationManager,
                       MailService mailService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.mailService = mailService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        UserEntity user = new UserEntity(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                request.getPhoneNumber()
        );

        user = userRepository.save(user);

        UserPrincipal principal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, toUserDto(user));
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserEntity user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, toUserDto(user));
    }

    @Transactional
    public AuthResponse authenticateGoogleUser(GoogleAuthRequest request) {
        if (googleClientId == null || googleClientId.trim().isEmpty() || googleClientId.contains("unconfigured")) {
            throw new BadRequestException("Google authentication is not configured on the server");
        }

        String googleSub;
        String email;
        String name;

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    new GsonFactory()
            )
            .setAudience(Collections.singletonList(googleClientId.trim()))
            .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());

            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                Boolean emailVerified = payload.getEmailVerified();
                if (emailVerified == null || !emailVerified) {
                    throw new BadRequestException("Unverified Google email address");
                }

                googleSub = payload.getSubject();
                email = payload.getEmail();
                name = (String) payload.get("name");
                if (name == null || name.trim().isEmpty()) {
                    name = email;
                }
            } else {
                throw new BadRequestException("Invalid or expired Google ID token");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to verify Google ID token: " + e.getMessage());
        }

        UserEntity user = userRepository.findByGoogleSubId(googleSub)
                .orElseGet(() -> userRepository.findByEmail(email).orElse(null));

        if (user == null) {
            user = new UserEntity(
                    email,
                    passwordEncoder.encode(UUID.randomUUID().toString()),
                    name,
                    ""
            );
            user.setGoogleSubId(googleSub);
            user = userRepository.save(user);
        } else if (user.getGoogleSubId() == null) {
            user.setGoogleSubId(googleSub);
            user = userRepository.save(user);
        }

        UserPrincipal principal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateToken(principal);
        String refreshToken = createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, toUserDto(user));
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        RefreshTokenEntity refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadRequestException("Refresh token has expired");
        }

        UserEntity user = refreshToken.getUser();
        UserPrincipal principal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateToken(principal);

        return new AuthResponse(newAccessToken, refreshTokenStr, toUserDto(user));
    }

    @Transactional
    public String requestPasswordReset(PasswordResetRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        String token = UUID.randomUUID().toString();
        PasswordResetTokenEntity resetToken = new PasswordResetTokenEntity();
        resetToken.setUser(user);
        resetToken.setToken(token);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        passwordResetTokenRepository.save(resetToken);

        boolean sent = mailService.sendPasswordResetMail(user.getEmail(), token);
        if (sent) {
            return "Password reset link sent to your email";
        } else {
            return "Reset token generated successfully: " + token + " (SMTP unconfigured in environment)";
        }
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException("Reset token has expired");
        }

        UserEntity user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toUserDto(user);
    }

    private String createRefreshToken(UserEntity user) {
        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    public UserDto toUserDto(UserEntity user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getPhoneNumber());
    }
}
