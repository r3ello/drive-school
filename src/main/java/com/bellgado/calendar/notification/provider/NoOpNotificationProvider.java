package com.bellgado.calendar.notification.provider;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * No-operation notification provider for development and testing.
 *
 * This provider:
 * - Logs all notification attempts
 * - Returns SENT status for all channels
 * - Generates fake message IDs for tracking
 *
 * Use this provider when:
 * - Developing and testing notification flows
 * - Running integration tests
 * - Staging environment without real messaging
 */
@Component
public class NoOpNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpNotificationProvider.class);

    @Override
    public String getName() {
        return "NOOP";
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        // NoOp supports all deliverable channels
        return channel.isDeliverable();
    }

    @Override
    public SendResult send(NotificationMessage message) {
        // Validate recipient
        if (message.recipient() == null || message.recipient().isBlank()) {
            log.warn("[NOOP] Skipping notification {} - no recipient for channel {}",
                    message.notificationId(), message.channel());
            return SendResult.skipped("No recipient provided for channel " + message.channel());
        }

        // Generate a fake message ID
        String fakeMessageId = "noop-" + UUID.randomUUID().toString().substring(0, 8);

        // Log the notification details
        log.info("[NOOP] Sending {} notification to {} ({}):\n" +
                        "  ID: {}\n" +
                        "  Type: {}\n" +
                        "  Template: {}\n" +
                        "  Subject: {}\n" +
                        "  Body: {}\n" +
                        "  Variables: {}\n" +
                        "  Fake Message ID: {}",
                message.channel(),
                message.recipient(),
                message.recipientName(),
                message.notificationId(),
                message.type(),
                message.templateKey(),
                message.subject(),
                truncate(message.body(), 200),
                message.variables(),
                fakeMessageId
        );

        // Simulate successful send
        return SendResult.sent(fakeMessageId);
    }

    @Override
    public int getPriority() {
        // Lowest priority - real providers should take precedence
        return Integer.MIN_VALUE;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
