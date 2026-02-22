package com.bellgado.calendar.notification;

import com.bellgado.calendar.domain.entity.Notification;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.infrastructure.repository.NotificationRepository;
import com.bellgado.calendar.infrastructure.repository.StudentRepository;
import com.bellgado.calendar.notification.provider.NotificationMessage;
import com.bellgado.calendar.notification.provider.SendResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Scheduled job for processing pending notifications from the outbox.
 *
 * <p>This job:
 * <ul>
 *   <li>Polls for PENDING notifications ready for processing</li>
 *   <li>Dispatches them through the NotificationDispatcher</li>
 *   <li>Handles retries with exponential backoff</li>
 *   <li>Marks expired notifications</li>
 * </ul>
 *
 * <p>Enable this job by setting:
 * <pre>
 * notifications.scheduler.enabled=true
 * </pre>
 *
 * <p><strong>Idempotency:</strong> The job uses status-based filtering to avoid
 * processing the same notification twice. In a distributed environment,
 * consider adding distributed locking or using SELECT FOR UPDATE SKIP LOCKED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notifications.scheduler.enabled", havingValue = "true")
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties properties;
    private final NotificationMessageFactory messageFactory;

    @PostConstruct
    void init() {
        log.info("NotificationScheduler initialized - will poll every {}",
                properties.getScheduler().getPollInterval());
    }

    /**
     * Main processing loop. Runs at the configured interval.
     */
    @Scheduled(fixedDelayString = "${notifications.scheduler.poll-interval:PT30S}")
    public void processNotifications() {
        if (!properties.isEnabled()) {
            return;
        }

        log.debug("Starting notification processing cycle");

        try {
            // First, mark expired notifications
            markExpiredNotifications();

            // Then process pending notifications
            int processed = processPendingNotifications();

            if (processed > 0) {
                log.info("Processed {} notifications in this cycle", processed);
            }
        } catch (Exception e) {
            log.error("Error in notification processing cycle", e);
        }
    }

    /**
     * Marks expired notifications that haven't been sent yet.
     */
    @Transactional
    public void markExpiredNotifications() {
        int marked = notificationRepository.markExpired(
                Set.of(NotificationStatus.PENDING, NotificationStatus.PROCESSING),
                NotificationStatus.EXPIRED,
                OffsetDateTime.now()
        );

        if (marked > 0) {
            log.info("Marked {} notifications as expired", marked);
        }
    }

    /**
     * Processes a batch of pending notifications.
     */
    @Transactional
    public int processPendingNotifications() {
        int batchSize = properties.getScheduler().getBatchSize();
        OffsetDateTime now = OffsetDateTime.now();

        List<Notification> notifications = notificationRepository.findReadyForProcessing(
                NotificationStatus.PENDING,
                now,
                PageRequest.of(0, batchSize)
        );

        if (notifications.isEmpty()) {
            return 0;
        }

        log.debug("Found {} pending notifications to process", notifications.size());

        int successCount = 0;
        for (Notification notification : notifications) {
            try {
                boolean success = processNotification(notification);
                if (success) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Error processing notification {}", notification.getId(), e);
                notification.markFailed("PROCESSING_ERROR", e.getMessage());
                notificationRepository.save(notification);
            }
        }

        return successCount;
    }

    /**
     * Processes a single notification.
     */
    private boolean processNotification(Notification notification) {
        log.debug("Processing notification {}", notification.getId());

        // Check if already expired
        if (notification.isExpired()) {
            notification.markExpired();
            notificationRepository.save(notification);
            return false;
        }

        // Check if scheduled for later
        if (notification.isScheduledForLater()) {
            log.debug("Notification {} is scheduled for later: {}", notification.getId(), notification.getScheduledFor());
            return false;
        }

        // Get the student
        Student student = studentRepository.findById(notification.getStudentId()).orElse(null);
        if (student == null) {
            notification.markSkipped("Student not found");
            notificationRepository.save(notification);
            return false;
        }

        // Validate eligibility
        if (!student.isActive()) {
            notification.markSkipped("Student is inactive");
            notificationRepository.save(notification);
            return false;
        }

        if (!student.isNotificationOptIn()) {
            notification.markSkipped("Student has not opted in");
            notificationRepository.save(notification);
            return false;
        }

        if (!student.canReceiveNotificationsOn(notification.getChannel())) {
            notification.markSkipped("Student cannot receive on channel " + notification.getChannel());
            notificationRepository.save(notification);
            return false;
        }

        // Mark as processing
        notification.markProcessing();
        notificationRepository.save(notification);

        // Build and dispatch message
        NotificationMessage message = messageFactory.build(notification, student);
        SendResult result = dispatcher.dispatch(message);

        // Update based on result
        switch (result.status()) {
            case SENT, DELIVERED -> {
                notification.markSent(result.providerMessageId());
                if (result.status() == SendResult.Status.DELIVERED) {
                    notification.setStatus(NotificationStatus.DELIVERED);
                }
            }
            case SKIPPED -> notification.markSkipped(result.errorMessage());
            case FAILED -> {
                if (result.retryable() && notification.canRetry()) {
                    notification.markFailed(result.errorCode(), result.errorMessage());
                } else {
                    notification.setStatus(NotificationStatus.FAILED);
                    notification.setErrorCode(result.errorCode());
                    notification.setErrorMessage(result.errorMessage());
                }
            }
        }

        notificationRepository.save(notification);
        return result.success();
    }

}
