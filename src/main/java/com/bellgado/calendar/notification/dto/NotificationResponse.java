package com.bellgado.calendar.notification.dto;

import com.bellgado.calendar.domain.entity.Notification;
import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.domain.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for a notification.
 */
public record NotificationResponse(
        UUID id,
        UUID studentId,
        NotificationChannel channel,
        NotificationType type,
        String templateKey,
        Map<String, String> variables,
        NotificationStatus status,
        int attempts,
        int maxAttempts,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime nextAttemptAt,
        String externalMessageId,
        String errorCode,
        String errorMessage,
        int priority,
        OffsetDateTime scheduledFor,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime sentAt
) {
    /**
     * Creates a response from a Notification entity.
     */
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getStudentId(),
                notification.getChannel(),
                notification.getType(),
                notification.getTemplateKey(),
                notification.getVariables(),
                notification.getStatus(),
                notification.getAttempts(),
                notification.getMaxAttempts(),
                notification.getLastAttemptAt(),
                notification.getNextAttemptAt(),
                notification.getExternalMessageId(),
                notification.getErrorCode(),
                notification.getErrorMessage(),
                notification.getPriority(),
                notification.getScheduledFor(),
                notification.getExpiresAt(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                notification.getSentAt()
        );
    }
}
