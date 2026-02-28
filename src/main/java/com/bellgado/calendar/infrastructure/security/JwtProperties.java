package com.bellgado.calendar.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** Base64-encoded 256-bit secret key (set via JWT_SECRET env var). */
    private String secret;

    /** Access token lifetime in seconds (default: 15 minutes). */
    private long accessTokenExpiration = 900;

    /** Refresh token lifetime in seconds (default: 30 days). */
    private long refreshTokenExpiration = 2592000;
}
