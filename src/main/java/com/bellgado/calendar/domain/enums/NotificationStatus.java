package com.bellgado.calendar.domain.enums;

/**
 * Status of a notification in the outbox.
 */
public enum NotificationStatus {
    /**
     * Notification is queued and waiting to be processed.
     */
    PENDING,

    /**
     * Notification is currently being processed by a worker.
     */
    PROCESSING,

    /**
     * Notification was successfully sent to the provider.
     */
    SENT,

    /**
     * Notification was confirmed as delivered by the provider.
     */
    DELIVERED,

    /**
     * Notification failed after all retry attempts.
     */
    FAILED,

    /**
     * Notification was skipped (e.g., student opted out, quiet hours, invalid contact).
     */
    SKIPPED,

    /**
     * Notification expired before it could be sent.
     */
    EXPIRED;

    /**
     * Checks if this status indicates a terminal state (no more processing needed).
     */
    public boolean isTerminal() {
        return this == SENT || this == DELIVERED || this == FAILED || this == SKIPPED || this == EXPIRED;
    }

    /**
     * Checks if this status indicates the notification can be retried.
     */
    public boolean canRetry() {
        return this == PENDING || this == PROCESSING;
    }
}
