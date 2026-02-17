package com.bellgado.calendar.domain.entity;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.domain.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    private Notification notification;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        notification = new Notification(studentId, NotificationChannel.EMAIL, NotificationType.CLASS_SCHEDULED);
        notification.setId(UUID.randomUUID());
        notification.setMaxAttempts(3);
    }

    @Nested
    class Construction {

        @Test
        void shouldCreateWithRequiredFields() {
            Notification n = new Notification(studentId, NotificationChannel.SMS, NotificationType.CLASS_CANCELLED);

            assertEquals(studentId, n.getStudentId());
            assertEquals(NotificationChannel.SMS, n.getChannel());
            assertEquals(NotificationType.CLASS_CANCELLED, n.getType());
            assertEquals("CLASS_CANCELLED", n.getTemplateKey());
            assertEquals(NotificationStatus.PENDING, n.getStatus());
            assertEquals(0, n.getAttempts());
        }

        @Test
        void shouldSetDefaultTemplateKeyFromType() {
            Notification n = new Notification(studentId, NotificationChannel.EMAIL, NotificationType.CLASS_REMINDER);
            assertEquals("CLASS_REMINDER", n.getTemplateKey());
        }

        @Test
        void shouldInitializeEmptyVariables() {
            Notification n = new Notification(studentId, NotificationChannel.EMAIL, NotificationType.CUSTOM);
            assertNotNull(n.getVariables());
            assertTrue(n.getVariables().isEmpty());
        }
    }

    @Nested
    class StatusTransitions {

        @Test
        void shouldMarkAsProcessing() {
            notification.markProcessing();

            assertEquals(NotificationStatus.PROCESSING, notification.getStatus());
            assertEquals(1, notification.getAttempts());
            assertNotNull(notification.getLastAttemptAt());
        }

        @Test
        void shouldIncrementAttemptsOnEachProcessing() {
            notification.markProcessing();
            assertEquals(1, notification.getAttempts());

            notification.setStatus(NotificationStatus.PENDING);
            notification.markProcessing();
            assertEquals(2, notification.getAttempts());

            notification.setStatus(NotificationStatus.PENDING);
            notification.markProcessing();
            assertEquals(3, notification.getAttempts());
        }

        @Test
        void shouldMarkAsSent() {
            String messageId = "msg-12345";
            notification.markSent(messageId);

            assertEquals(NotificationStatus.SENT, notification.getStatus());
            assertEquals(messageId, notification.getExternalMessageId());
            assertNotNull(notification.getSentAt());
            assertNull(notification.getErrorMessage());
            assertNull(notification.getErrorCode());
        }

        @Test
        void shouldClearErrorOnSuccess() {
            notification.setErrorMessage("Previous error");
            notification.setErrorCode("PREV_ERROR");

            notification.markSent("new-msg-id");

            assertNull(notification.getErrorMessage());
            assertNull(notification.getErrorCode());
        }

        @Test
        void shouldMarkAsSkipped() {
            String reason = "Student opted out";
            notification.markSkipped(reason);

            assertEquals(NotificationStatus.SKIPPED, notification.getStatus());
            assertEquals(reason, notification.getErrorMessage());
        }

        @Test
        void shouldMarkAsExpired() {
            notification.markExpired();

            assertEquals(NotificationStatus.EXPIRED, notification.getStatus());
        }
    }

    @Nested
    class RetryBehavior {

        @Test
        void shouldRetryWhenAttemptsRemain() {
            notification.setMaxAttempts(3);
            notification.setAttempts(1);
            notification.setStatus(NotificationStatus.PENDING);

            assertTrue(notification.canRetry());
        }

        @Test
        void shouldNotRetryWhenMaxAttemptsReached() {
            notification.setMaxAttempts(3);
            notification.setAttempts(3);
            notification.setStatus(NotificationStatus.PENDING);

            assertFalse(notification.canRetry());
        }

        @Test
        void shouldNotRetryWhenInTerminalState() {
            notification.setAttempts(1);
            notification.setMaxAttempts(3);

            notification.setStatus(NotificationStatus.SENT);
            assertFalse(notification.canRetry());

            notification.setStatus(NotificationStatus.FAILED);
            assertFalse(notification.canRetry());

            notification.setStatus(NotificationStatus.SKIPPED);
            assertFalse(notification.canRetry());

            notification.setStatus(NotificationStatus.EXPIRED);
            assertFalse(notification.canRetry());
        }

        @Test
        void shouldScheduleRetryOnFailureWithAttemptsRemaining() {
            notification.setMaxAttempts(3);
            notification.markProcessing();

            OffsetDateTime beforeFail = OffsetDateTime.now();
            notification.markFailed("TEMP_ERROR", "Temporary failure");

            assertEquals(NotificationStatus.PENDING, notification.getStatus());
            assertEquals("TEMP_ERROR", notification.getErrorCode());
            assertEquals("Temporary failure", notification.getErrorMessage());
            assertNotNull(notification.getNextAttemptAt());
            assertTrue(notification.getNextAttemptAt().isAfter(beforeFail));
        }

        @Test
        void shouldMarkAsFailedWhenNoAttemptsRemaining() {
            notification.setMaxAttempts(2);
            notification.markProcessing();
            notification.setStatus(NotificationStatus.PENDING);
            notification.markProcessing();

            notification.markFailed("PERM_ERROR", "Permanent failure");

            assertEquals(NotificationStatus.FAILED, notification.getStatus());
            assertEquals("PERM_ERROR", notification.getErrorCode());
        }

        @Test
        void shouldApplyExponentialBackoff() {
            notification.setMaxAttempts(5);

            notification.markProcessing();
            OffsetDateTime time1 = OffsetDateTime.now();
            notification.markFailed("ERR", "msg");
            OffsetDateTime next1 = notification.getNextAttemptAt();
            assertTrue(next1.isAfter(time1));

            notification.markProcessing();
            OffsetDateTime time2 = OffsetDateTime.now();
            notification.markFailed("ERR", "msg");
            OffsetDateTime next2 = notification.getNextAttemptAt();

            long delay1Minutes = java.time.Duration.between(time1, next1).toMinutes();
            long delay2Minutes = java.time.Duration.between(time2, next2).toMinutes();

            assertTrue(delay2Minutes >= delay1Minutes,
                    "Second delay should be >= first delay (exponential backoff)");
        }
    }

    @Nested
    class ExpirationBehavior {

        @Test
        void shouldBeExpiredWhenPastExpiryTime() {
            notification.setExpiresAt(OffsetDateTime.now().minusHours(1));

            assertTrue(notification.isExpired());
        }

        @Test
        void shouldNotBeExpiredWhenBeforeExpiryTime() {
            notification.setExpiresAt(OffsetDateTime.now().plusHours(1));

            assertFalse(notification.isExpired());
        }

        @Test
        void shouldNotBeExpiredWhenNoExpirySet() {
            notification.setExpiresAt(null);

            assertFalse(notification.isExpired());
        }
    }

    @Nested
    class ScheduledDelivery {

        @Test
        void shouldBeScheduledForLaterWhenInFuture() {
            notification.setScheduledFor(OffsetDateTime.now().plusHours(1));

            assertTrue(notification.isScheduledForLater());
        }

        @Test
        void shouldNotBeScheduledForLaterWhenInPast() {
            notification.setScheduledFor(OffsetDateTime.now().minusMinutes(1));

            assertFalse(notification.isScheduledForLater());
        }

        @Test
        void shouldNotBeScheduledForLaterWhenNotSet() {
            notification.setScheduledFor(null);

            assertFalse(notification.isScheduledForLater());
        }
    }

    @Nested
    class Variables {

        @Test
        void shouldSetAndGetVariables() {
            Map<String, String> vars = Map.of(
                    "studentName", "John Doe",
                    "className", "Piano Lesson",
                    "time", "10:00 AM"
            );

            notification.setVariables(vars);

            assertEquals(3, notification.getVariables().size());
            assertEquals("John Doe", notification.getVariables().get("studentName"));
        }

        @Test
        void shouldHandleNullVariables() {
            notification.setVariables(null);

            assertNotNull(notification.getVariables());
            assertTrue(notification.getVariables().isEmpty());
        }
    }

    @Nested
    class RenderedContent {

        @Test
        void shouldStoreRenderedContent() {
            notification.setRenderedSubject("Your Class on Monday");
            notification.setRenderedBody("Dear John, your piano lesson is scheduled...");

            assertEquals("Your Class on Monday", notification.getRenderedSubject());
            assertEquals("Dear John, your piano lesson is scheduled...", notification.getRenderedBody());
        }
    }

    @Nested
    class Priority {

        @Test
        void shouldDefaultToZeroPriority() {
            Notification n = new Notification(studentId, NotificationChannel.EMAIL, NotificationType.CLASS_SCHEDULED);
            assertEquals(0, n.getPriority());
        }

        @Test
        void shouldAllowSettingPriority() {
            notification.setPriority(10);
            assertEquals(10, notification.getPriority());
        }
    }
}
