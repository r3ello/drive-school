package com.bellgado.calendar.notification.provider;

/**
 * Result of sending a notification through a provider.
 */
public record SendResult(
        /**
         * Whether the send operation was successful.
         */
        boolean success,

        /**
         * Status of the delivery.
         */
        Status status,

        /**
         * Provider's message ID for tracking.
         */
        String providerMessageId,

        /**
         * Error code if failed.
         */
        String errorCode,

        /**
         * Error message if failed.
         */
        String errorMessage,

        /**
         * Whether this error is retryable.
         */
        boolean retryable
) {
    public enum Status {
        /**
         * Message was sent successfully.
         */
        SENT,

        /**
         * Message was delivered (provider confirmed).
         */
        DELIVERED,

        /**
         * Message was skipped (e.g., opt-out, invalid recipient).
         */
        SKIPPED,

        /**
         * Sending failed.
         */
        FAILED
    }

    /**
     * Creates a successful send result.
     */
    public static SendResult sent(String providerMessageId) {
        return new SendResult(true, Status.SENT, providerMessageId, null, null, false);
    }

    /**
     * Creates a delivered result.
     */
    public static SendResult delivered(String providerMessageId) {
        return new SendResult(true, Status.DELIVERED, providerMessageId, null, null, false);
    }

    /**
     * Creates a skipped result.
     */
    public static SendResult skipped(String reason) {
        return new SendResult(true, Status.SKIPPED, null, "SKIPPED", reason, false);
    }

    /**
     * Creates a failed result that can be retried.
     */
    public static SendResult failedRetryable(String errorCode, String errorMessage) {
        return new SendResult(false, Status.FAILED, null, errorCode, errorMessage, true);
    }

    /**
     * Creates a failed result that cannot be retried.
     */
    public static SendResult failedPermanent(String errorCode, String errorMessage) {
        return new SendResult(false, Status.FAILED, null, errorCode, errorMessage, false);
    }
}
