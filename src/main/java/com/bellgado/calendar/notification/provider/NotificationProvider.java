package com.bellgado.calendar.notification.provider;

import com.bellgado.calendar.domain.enums.NotificationChannel;

/**
 * Interface for notification delivery providers.
 *
 * Implementations should handle a specific channel (EMAIL, SMS, WHATSAPP)
 * and communicate with external services (SMTP, Twilio, Meta Cloud API, etc.).
 *
 * <h2>Extension Points</h2>
 * To add a new provider:
 * <ol>
 *   <li>Create a new class implementing this interface</li>
 *   <li>Annotate with @Component and optionally @ConditionalOnProperty</li>
 *   <li>Register the provider in application configuration</li>
 * </ol>
 *
 * <h2>Example Implementation</h2>
 * <pre>
 * {@code
 * @Component
 * @ConditionalOnProperty(name = "notifications.providers.sms", havingValue = "TWILIO")
 * public class TwilioSmsProvider implements NotificationProvider {
 *     @Override
 *     public boolean supports(NotificationChannel channel) {
 *         return channel == NotificationChannel.SMS;
 *     }
 *
 *     @Override
 *     public SendResult send(NotificationMessage message) {
 *         // Twilio API call
 *     }
 * }
 * }
 * </pre>
 */
public interface NotificationProvider {

    /**
     * Returns the unique name of this provider.
     * Used for logging and configuration.
     */
    String getName();

    /**
     * Checks if this provider supports the given channel.
     *
     * @param channel the notification channel
     * @return true if this provider can handle the channel
     */
    boolean supports(NotificationChannel channel);

    /**
     * Sends a notification message.
     *
     * <p>Implementations should:
     * <ul>
     *   <li>Validate the message before sending</li>
     *   <li>Handle rate limiting gracefully</li>
     *   <li>Return appropriate SendResult for success, failure, or skip</li>
     *   <li>Not throw exceptions for expected errors (use SendResult.failed instead)</li>
     * </ul>
     *
     * @param message the notification message to send
     * @return the result of the send operation
     * @throws RuntimeException only for unexpected system errors
     */
    SendResult send(NotificationMessage message);

    /**
     * Returns the priority of this provider.
     * Higher priority providers are preferred when multiple support the same channel.
     * Default is 0.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Checks if this provider is currently available/healthy.
     * Used for health checks and failover decisions.
     */
    default boolean isAvailable() {
        return true;
    }
}
