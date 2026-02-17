package com.bellgado.calendar.notification.provider;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * Message to be sent to a notification provider.
 * Contains all information needed to deliver the notification.
 */
public record NotificationMessage(
        /**
         * Internal notification ID for tracking.
         */
        UUID notificationId,

        /**
         * Target student ID.
         */
        UUID studentId,

        /**
         * Delivery channel.
         */
        NotificationChannel channel,

        /**
         * Notification type.
         */
        NotificationType type,

        /**
         * Recipient information based on channel:
         * - EMAIL: email address
         * - SMS: phone number in E.164 format
         * - WHATSAPP: WhatsApp number in E.164 format
         */
        String recipient,

        /**
         * Recipient display name (for personalization).
         */
        String recipientName,

        /**
         * Template key for template-based rendering.
         */
        String templateKey,

        /**
         * Variables for template substitution.
         */
        Map<String, String> variables,

        /**
         * Pre-rendered subject (for EMAIL).
         */
        String subject,

        /**
         * Pre-rendered body content.
         */
        String body,

        /**
         * Recipient's locale for localized content.
         */
        String locale
) {
    /**
     * Creates a builder for NotificationMessage.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID notificationId;
        private UUID studentId;
        private NotificationChannel channel;
        private NotificationType type;
        private String recipient;
        private String recipientName;
        private String templateKey;
        private Map<String, String> variables;
        private String subject;
        private String body;
        private String locale = "en";

        public Builder notificationId(UUID notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder studentId(UUID studentId) {
            this.studentId = studentId;
            return this;
        }

        public Builder channel(NotificationChannel channel) {
            this.channel = channel;
            return this;
        }

        public Builder type(NotificationType type) {
            this.type = type;
            return this;
        }

        public Builder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder recipientName(String recipientName) {
            this.recipientName = recipientName;
            return this;
        }

        public Builder templateKey(String templateKey) {
            this.templateKey = templateKey;
            return this;
        }

        public Builder variables(Map<String, String> variables) {
            this.variables = variables;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public NotificationMessage build() {
            return new NotificationMessage(
                    notificationId, studentId, channel, type,
                    recipient, recipientName, templateKey, variables,
                    subject, body, locale
            );
        }
    }
}
