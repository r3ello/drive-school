package com.bellgado.calendar.infrastructure.security;

import com.bellgado.calendar.domain.enums.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String email, UserRole role, UUID studentId) {
        long nowMs = System.currentTimeMillis();
        Date expiresAt = new Date(nowMs + jwtProperties.getAccessTokenExpiration() * 1000);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(new Date(nowMs))
                .expiration(expiresAt)
                .signWith(signingKey());

        if (studentId != null) {
            builder.claim("studentId", studentId.toString());
        }

        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UserRole getRole(String token) {
        return UserRole.valueOf(parseClaims(token).get("role", String.class));
    }

    public UUID getStudentId(String token) {
        String raw = parseClaims(token).get("studentId", String.class);
        return raw != null ? UUID.fromString(raw) : null;
    }

    public String getEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }
}
