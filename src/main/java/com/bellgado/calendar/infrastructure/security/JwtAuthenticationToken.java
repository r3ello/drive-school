package com.bellgado.calendar.infrastructure.security;

import com.bellgado.calendar.domain.enums.UserRole;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

/**
 * Authentication token that carries JWT claims directly.
 * Avoids a DB lookup on every request — all data is in the signed token.
 */
@Getter
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final UUID userId;
    private final String email;
    private final UserRole role;
    private final UUID studentId;

    public JwtAuthenticationToken(UUID userId, String email, UserRole role, UUID studentId,
                                  Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.studentId = studentId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}
