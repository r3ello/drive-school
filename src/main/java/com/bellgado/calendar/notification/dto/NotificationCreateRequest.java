package com.bellgado.calendar.notification.dto;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationType;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request to create a new notification.
 */
public record NotificationCreateRequest(
        /**
         * Target student ID (required).
         */
        @NotNull(message = "Student ID is required")
        UUID studentId,

        /**
         * Delivery channel. If null, uses student's preferred channel.
         */
        NotificationChannel channel,

        /**
         * Notification type (required).
         */
        @NotNull(message = "Notification type is required")
        NotificationType type,

        /**
         * Template key for content rendering. If null, uses type's default template.
         */
        String templateKey,

        /**
         * Variables for template substitution.
         */
        Map<String, String> variables,

        /**
         * Fallback channels if primary fails (for future use).
         */
        List<NotificationChannel> fallbackChannels,

        /**
         * Priority (higher = more urgent). Default is 0.
         */
        Integer priority,

        /**
         * Scheduled delivery time. If null, sends immediately.
         */
        OffsetDateTime scheduledFor,

        /**
         * Expiration time. If null, uses default from configuration.
         */
        OffsetDateTime expiresAt
) {
    /**
     * Creates a simple notification request with just student, type, and variables.
     */
    public static NotificationCreateRequest simple(UUID studentId, NotificationType type, Map<String, String> variables) {
        return new NotificationCreateRequest(studentId, null, type, null, variables, null, null, null, null);
    }
}
