package com.bellgado.calendar.notification.provider;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NoOpNotificationProviderTest {

    private NoOpNotificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NoOpNotificationProvider();
    }

    @Nested
    class ProviderMetadata {

        @Test
        void shouldReturnNoopName() {
            assertEquals("NOOP", provider.getName());
        }

        @Test
        void shouldHaveLowestPriority() {
            assertEquals(Integer.MIN_VALUE, provider.getPriority());
        }

        @Test
        void shouldAlwaysBeAvailable() {
            assertTrue(provider.isAvailable());
        }
    }

    @Nested
    class ChannelSupport {

        @Test
        void shouldSupportEmail() {
            assertTrue(provider.supports(NotificationChannel.EMAIL));
        }

        @Test
        void shouldSupportSms() {
            assertTrue(provider.supports(NotificationChannel.SMS));
        }

        @Test
        void shouldSupportWhatsApp() {
            assertTrue(provider.supports(NotificationChannel.WHATSAPP));
        }

        @Test
        void shouldNotSupportNoneChannel() {
            assertFalse(provider.supports(NotificationChannel.NONE));
        }

        @Test
        void shouldSupportAllDeliverableChannels() {
            for (NotificationChannel channel : NotificationChannel.values()) {
                if (channel.isDeliverable()) {
                    assertTrue(provider.supports(channel),
                            "Should support deliverable channel: " + channel);
                } else {
                    assertFalse(provider.supports(channel),
                            "Should not support non-deliverable channel: " + channel);
                }
            }
        }
    }

    @Nested
    class SendBehavior {

        @Test
        void shouldReturnSentStatusForValidEmailMessage() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .type(NotificationType.CLASS_SCHEDULED)
                    .recipient("john@example.com")
                    .recipientName("John Doe")
                    .templateKey("CLASS_SCHEDULED")
                    .variables(Map.of("className", "Piano Lesson"))
                    .subject("Your Class is Scheduled")
                    .body("Your piano lesson has been scheduled for Monday at 10 AM.")
                    .locale("en")
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SENT, result.status());
            assertNotNull(result.providerMessageId());
            assertTrue(result.providerMessageId().startsWith("noop-"));
            assertNull(result.errorCode());
            assertNull(result.errorMessage());
        }

        @Test
        void shouldReturnSentStatusForValidSmsMessage() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.SMS)
                    .type(NotificationType.CLASS_REMINDER)
                    .recipient("+12025551234")
                    .recipientName("Jane Smith")
                    .templateKey("CLASS_REMINDER")
                    .variables(Map.of("time", "10:00 AM"))
                    .body("Reminder: Your class is in 1 hour.")
                    .locale("en")
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SENT, result.status());
        }

        @Test
        void shouldReturnSentStatusForValidWhatsAppMessage() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.WHATSAPP)
                    .type(NotificationType.CLASS_CANCELLED)
                    .recipient("+12025551234")
                    .recipientName("Bob Wilson")
                    .templateKey("CLASS_CANCELLED")
                    .variables(Map.of("reason", "Teacher sick"))
                    .body("Unfortunately, your class has been cancelled.")
                    .locale("es")
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SENT, result.status());
        }

        @Test
        void shouldSkipWhenRecipientIsNull() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .type(NotificationType.CLASS_SCHEDULED)
                    .recipient(null)
                    .recipientName("John Doe")
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SKIPPED, result.status());
            assertNotNull(result.errorMessage());
            assertTrue(result.errorMessage().contains("No recipient"));
        }

        @Test
        void shouldSkipWhenRecipientIsEmpty() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.SMS)
                    .type(NotificationType.CLASS_REMINDER)
                    .recipient("")
                    .recipientName("Jane Smith")
                    .build();

            SendResult result = provider.send(message);

            assertEquals(SendResult.Status.SKIPPED, result.status());
            assertTrue(result.errorMessage().contains("No recipient"));
        }

        @Test
        void shouldSkipWhenRecipientIsBlank() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.WHATSAPP)
                    .type(NotificationType.CLASS_RESCHEDULED)
                    .recipient("   ")
                    .recipientName("Test User")
                    .build();

            SendResult result = provider.send(message);

            assertEquals(SendResult.Status.SKIPPED, result.status());
        }

        @Test
        void shouldGenerateUniqueMessageIds() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .type(NotificationType.CLASS_SCHEDULED)
                    .recipient("test@example.com")
                    .recipientName("Test")
                    .build();

            SendResult result1 = provider.send(message);
            SendResult result2 = provider.send(message);

            assertNotEquals(result1.providerMessageId(), result2.providerMessageId());
        }

        @Test
        void shouldHandleEmptyVariables() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .type(NotificationType.CUSTOM)
                    .recipient("test@example.com")
                    .recipientName("Test User")
                    .templateKey("CUSTOM_MESSAGE")
                    .variables(Map.of())
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SENT, result.status());
        }

        @Test
        void shouldHandleNullVariables() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .type(NotificationType.CLASS_SCHEDULED)
                    .recipient("test@example.com")
                    .recipientName("Test User")
                    .variables(null)
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.success());
            assertEquals(SendResult.Status.SENT, result.status());
        }

        @Test
        void shouldHandleAllNotificationTypes() {
            for (NotificationType type : NotificationType.values()) {
                NotificationMessage message = NotificationMessage.builder()
                        .notificationId(UUID.randomUUID())
                        .studentId(UUID.randomUUID())
                        .channel(NotificationChannel.EMAIL)
                        .type(type)
                        .recipient("test@example.com")
                        .recipientName("Test User")
                        .templateKey(type.getDefaultTemplateKey())
                        .build();

                SendResult result = provider.send(message);

                assertTrue(result.success(),
                        "Should successfully send notification type: " + type);
            }
        }
    }

    @Nested
    class SendResultDetails {

        @Test
        void shouldReturnNonRetryableResult() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.EMAIL)
                    .type(NotificationType.CLASS_SCHEDULED)
                    .recipient("test@example.com")
                    .recipientName("Test")
                    .build();

            SendResult result = provider.send(message);

            assertFalse(result.retryable());
        }

        @Test
        void shouldIncludeChannelInSkipReason() {
            NotificationMessage message = NotificationMessage.builder()
                    .notificationId(UUID.randomUUID())
                    .studentId(UUID.randomUUID())
                    .channel(NotificationChannel.SMS)
                    .type(NotificationType.CLASS_SCHEDULED)
                    .recipient(null)
                    .recipientName("Test")
                    .build();

            SendResult result = provider.send(message);

            assertTrue(result.errorMessage().contains("SMS"));
        }
    }
}
