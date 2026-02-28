package com.bellgado.calendar.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * CORS configuration is now managed centrally in SecurityConfig.corsConfigurationSource().
 * This class is retained as a placeholder.
 *
 * Allowed origins are read from: sse.cors.allowed-origins (application.yaml)
 * Covered paths: /api/**
 */
@Configuration
public class CorsConfig {
    // CORS is configured via SecurityConfig.corsConfigurationSource()
}
