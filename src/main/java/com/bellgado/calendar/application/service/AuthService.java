package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.auth.*;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.RefreshToken;
import com.bellgado.calendar.domain.entity.User;
import com.bellgado.calendar.domain.enums.UserStatus;
import com.bellgado.calendar.infrastructure.repository.RefreshTokenRepository;
import com.bellgado.calendar.infrastructure.repository.UserRepository;
import com.bellgado.calendar.infrastructure.security.JwtAuthenticationToken;
import com.bellgado.calendar.infrastructure.security.JwtProperties;
import com.bellgado.calendar.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getStatus() == UserStatus.PENDING_CONFIRMATION) {
            throw new InvalidStateException("Account not yet confirmed. Check your email.");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new InvalidStateException("Account is inactive.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(), user.getStudentId());
        String rawRefreshToken = issueRefreshToken(user.getId(), null);

        return LoginResponse.of(accessToken, rawRefreshToken,
                jwtProperties.getAccessTokenExpiration(), user.isPasswordChangeRequired());
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked() || stored.isExpired()) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        String rawRefreshToken = issueRefreshToken(user.getId(), null);
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(), user.getStudentId());

        return LoginResponse.of(accessToken, rawRefreshToken,
                jwtProperties.getAccessTokenExpiration(), user.isPasswordChangeRequired());
    }

    @Transactional
    public void confirmEmail(String token) {
        User user = userRepository.findByConfirmationToken(token)
                .orElseThrow(() -> new NotFoundException("Invalid or expired confirmation link"));

        if (user.getConfirmationTokenExpiresAt() != null
                && OffsetDateTime.now().isAfter(user.getConfirmationTokenExpiresAt())) {
            throw new InvalidStateException("Confirmation link has expired. Ask for a new invitation.");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setConfirmationToken(null);
        user.setConfirmationTokenExpiresAt(null);
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        UUID userId = extractUserId(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ConflictException("New password must differ from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangeRequired(false);

        // Revoke all existing refresh tokens to force re-login with new password
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> rt.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return UserResponse.from(user);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String issueRefreshToken(UUID userId, String deviceInfo) {
        String raw = UUID.randomUUID().toString();
        String hash = sha256(raw);

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(OffsetDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpiration()));
        rt.setDeviceInfo(deviceInfo);
        refreshTokenRepository.save(rt);

        return raw;
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getUserId();
        }
        throw new InvalidStateException("Unexpected authentication type");
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
