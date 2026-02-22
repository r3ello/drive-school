package com.bellgado.calendar.notification;

import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Notification;
import com.bellgado.calendar.domain.entity.Student;
import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.domain.enums.NotificationType;
import com.bellgado.calendar.infrastructure.repository.NotificationRepository;
import com.bellgado.calendar.infrastructure.repository.StudentRepository;
import com.bellgado.calendar.notification.dto.NotificationCreateRequest;
import com.bellgado.calendar.notification.dto.NotificationResponse;
import com.bellgado.calendar.notification.provider.NotificationMessage;
import com.bellgado.calendar.notification.provider.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for creating and managing notifications.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Creating notifications in the outbox</li>
 *   <li>Validating student eligibility for notifications</li>
 *   <li>Dispatching notifications (immediately or scheduled)</li>
 *   <li>Querying notification history</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties properties;
    private final NotificationMessageFactory messageFactory;

    /**
     * Creates a notification for a student.
     *
     * <p>Validation:
     * <ul>
     *   <li>Student must exist and be active</li>
     *   <li>Student must have opted in for notifications</li>
     *   <li>Student must have valid contact info for the channel</li>
     * </ul>
     *
     * @param request the notification request
     * @return the created notification
     */
    @Transactional
    public NotificationResponse create(NotificationCreateRequest request) {
        if (!properties.isEnabled()) {
            log.debug("Notifications disabled, skipping creation for student {}", request.studentId());
            return null;
        }

        // Validate student
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new NotFoundException("Student not found: " + request.studentId()));

        // Determine channel
        NotificationChannel channel = request.channel() != null
                ? request.channel()
                : student.getPreferredNotificationChannel();

        // Validate eligibility
        ValidationResult validation = validateEligibility(student, channel);

        // Create notification entity
        Notification notification = new Notification(request.studentId(), channel, request.type());
        notification.setTemplateKey(request.templateKey() != null ? request.templateKey() : request.type().getDefaultTemplateKey());
        notification.setVariables(request.variables() != null ? request.variables() : new HashMap<>());
        notification.setMaxAttempts(properties.getDefaults().getMaxAttempts());
        notification.setPriority(request.priority() != null ? request.priority() : properties.getDefaults().getPriority());

        // Set expiry
        if (request.expiresAt() != null) {
            notification.setExpiresAt(request.expiresAt());
        } else {
            notification.setExpiresAt(OffsetDateTime.now().plus(properties.getDefaults().getExpiry()));
        }

        // Set scheduled time
        if (request.scheduledFor() != null) {
            notification.setScheduledFor(request.scheduledFor());
        }

        // Handle ineligible students
        if (!validation.eligible()) {
            notification.markSkipped(validation.reason());
            notification = notificationRepository.save(notification);
            log.info("Notification {} skipped for student {}: {}", notification.getId(), student.getId(), validation.reason());
            return NotificationResponse.from(notification);
        }

        // Save notification
        notification = notificationRepository.save(notification);
        log.info("Notification {} created for student {} via {}", notification.getId(), student.getId(), channel);

        // Immediate dispatch if configured
        if (properties.isImmediateDispatch() && !notification.isScheduledForLater()) {
            dispatchNow(notification, student);
        }

        return NotificationResponse.from(notification);
    }

    /**
     * Creates notifications for an event (e.g., slot booking, cancellation).
     * This is a convenience method for event-driven notifications.
     */
    @Transactional
    public NotificationResponse createForEvent(
            UUID studentId,
            NotificationType type,
            Map<String, String> variables) {

        NotificationCreateRequest request = new NotificationCreateRequest(
                studentId,
                null, // Use student's preferred channel
                type,
                null, // Use default template
                variables,
                null, // No fallback channels for now
                null, // Default priority
                null, // No specific schedule
                null  // Default expiry
        );

        return create(request);
    }

    /**
     * Retrieves a notification by ID.
     */
    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        return NotificationResponse.from(notification);
    }

    /**
     * Lists notifications with optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Collection<NotificationStatus> statuses, Pageable pageable) {
        if (statuses == null || statuses.isEmpty()) {
            return notificationRepository.findAll(pageable).map(NotificationResponse::from);
        }
        return notificationRepository.findByStatusInOrderByCreatedAtDesc(statuses, pageable)
                .map(NotificationResponse::from);
    }

    /**
     * Lists notifications for a specific student.
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listByStudent(UUID studentId, Pageable pageable) {
        return notificationRepository.findByStudentIdOrderByCreatedAtDesc(studentId, pageable)
                .map(NotificationResponse::from);
    }

    /**
     * Processes a single notification (for manual dispatch or scheduler).
     */
    @Transactional
    public NotificationResponse process(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));

        if (notification.getStatus().isTerminal()) {
            log.debug("Notification {} already in terminal state: {}", notificationId, notification.getStatus());
            return NotificationResponse.from(notification);
        }

        Student student = studentRepository.findById(notification.getStudentId())
                .orElse(null);

        if (student == null) {
            notification.markSkipped("Student not found");
            notification = notificationRepository.save(notification);
            return NotificationResponse.from(notification);
        }

        dispatchNow(notification, student);
        return NotificationResponse.from(notification);
    }

    /**
     * Gets notification statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        for (NotificationStatus status : NotificationStatus.values()) {
            stats.put(status.name(), notificationRepository.countByStatus(status));
        }
        return stats;
    }

    // ============================================================================
    // PRIVATE METHODS
    // ============================================================================

    private void dispatchNow(Notification notification, Student student) {
        // Check expiry
        if (notification.isExpired()) {
            notification.markExpired();
            notificationRepository.save(notification);
            log.info("Notification {} expired", notification.getId());
            return;
        }

        // Re-validate eligibility
        ValidationResult validation = validateEligibility(student, notification.getChannel());
        if (!validation.eligible()) {
            notification.markSkipped(validation.reason());
            notificationRepository.save(notification);
            log.info("Notification {} skipped: {}", notification.getId(), validation.reason());
            return;
        }

        // Mark as processing
        notification.markProcessing();
        notificationRepository.save(notification);

        // Build message
        NotificationMessage message = messageFactory.build(notification, student);

        // Dispatch
        SendResult result = dispatcher.dispatch(message);

        // Update notification based on result
        switch (result.status()) {
            case SENT, DELIVERED -> {
                notification.markSent(result.providerMessageId());
                if (result.status() == SendResult.Status.DELIVERED) {
                    notification.setStatus(NotificationStatus.DELIVERED);
                }
            }
            case SKIPPED -> notification.markSkipped(result.errorMessage());
            case FAILED -> {
                if (result.retryable()) {
                    notification.markFailed(result.errorCode(), result.errorMessage());
                } else {
                    notification.setStatus(NotificationStatus.FAILED);
                    notification.setErrorCode(result.errorCode());
                    notification.setErrorMessage(result.errorMessage());
                }
            }
        }

        notificationRepository.save(notification);
    }

    private ValidationResult validateEligibility(Student student, NotificationChannel channel) {
        if (!student.isActive()) {
            return new ValidationResult(false, "Student is inactive");
        }

        if (!channel.isDeliverable()) {
            return new ValidationResult(false, "Channel " + channel + " is not deliverable");
        }

        if (!student.isNotificationOptIn()) {
            return new ValidationResult(false, "Student has not opted in for notifications");
        }

        if (!student.canReceiveNotificationsOn(channel)) {
            return new ValidationResult(false, "Student cannot receive notifications on channel " + channel);
        }

        if (!dispatcher.hasProvider(channel)) {
            return new ValidationResult(false, "No provider available for channel " + channel);
        }

        return new ValidationResult(true, null);
    }

    private record ValidationResult(boolean eligible, String reason) {}
}
