package com.bellgado.calendar.domain.entity;

import com.bellgado.calendar.domain.enums.NotificationChannel;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import com.bellgado.calendar.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notification outbox entity.
 * Stores notifications to be sent asynchronously with retry support.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_status_next_attempt", columnList = "status, next_attempt_at"),
    @Index(name = "idx_notifications_student_id", columnList = "student_id"),
    @Index(name = "idx_notifications_student_created", columnList = "student_id, created_at DESC"),
    @Index(name = "idx_notifications_type", columnList = "type"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    // ============================================================================
    // TARGET
    // ============================================================================

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // ============================================================================
    // CHANNEL AND TYPE
    // ============================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    // ============================================================================
    // CONTENT (TEMPLATE-BASED)
    // ============================================================================

    @Column(name = "template_key", length = 100)
    private String templateKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private Map<String, String> variables = new HashMap<>();

    // ============================================================================
    // RENDERED CONTENT
    // ============================================================================

    @Column(name = "rendered_subject", length = 500)
    private String renderedSubject;

    @Column(name = "rendered_body", columnDefinition = "TEXT")
    private String renderedBody;

    // ============================================================================
    // DELIVERY STATUS
    // ============================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    // ============================================================================
    // RETRY TRACKING
    // ============================================================================

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    // ============================================================================
    // PROVIDER RESPONSE
    // ============================================================================

    @Column(name = "external_message_id", length = 200)
    private String externalMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    // ============================================================================
    // METADATA
    // ============================================================================

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Column(name = "scheduled_for")
    private OffsetDateTime scheduledFor;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    // ============================================================================
    // AUDIT
    // ============================================================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ============================================================================
    // CONSTRUCTORS
    // ============================================================================

    public Notification(UUID studentId, NotificationChannel channel, NotificationType type) {
        this.studentId = studentId;
        this.channel = channel;
        this.type = type;
        this.templateKey = type.getDefaultTemplateKey();
    }

    // ============================================================================
    // CUSTOM SETTER (null-guard)
    // ============================================================================

    public void setVariables(Map<String, String> variables) {
        this.variables = variables != null ? variables : new HashMap<>();
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Checks if this notification can be retried.
     */
    public boolean canRetry() {
        return status.canRetry() && attempts < maxAttempts;
    }

    /**
     * Marks this notification as processing and increments the attempt counter.
     */
    public void markProcessing() {
        this.status = NotificationStatus.PROCESSING;
        this.attempts++;
        this.lastAttemptAt = OffsetDateTime.now();
    }

    /**
     * Marks this notification as sent successfully.
     */
    public void markSent(String externalMessageId) {
        this.status = NotificationStatus.SENT;
        this.externalMessageId = externalMessageId;
        this.sentAt = OffsetDateTime.now();
        this.errorMessage = null;
        this.errorCode = null;
    }

    /**
     * Marks this notification as failed.
     * If retries are available, schedules the next attempt.
     */
    public void markFailed(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;

        if (canRetry()) {
            this.status = NotificationStatus.PENDING;
            // Exponential backoff: 1min, 5min, 15min, etc.
            long delayMinutes = (long) Math.pow(5, attempts - 1);
            this.nextAttemptAt = OffsetDateTime.now().plusMinutes(delayMinutes);
        } else {
            this.status = NotificationStatus.FAILED;
        }
    }

    /**
     * Marks this notification as skipped (e.g., opt-out, invalid contact).
     */
    public void markSkipped(String reason) {
        this.status = NotificationStatus.SKIPPED;
        this.errorMessage = reason;
    }

    /**
     * Marks this notification as expired.
     */
    public void markExpired() {
        this.status = NotificationStatus.EXPIRED;
    }

    /**
     * Checks if the notification has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if the notification is scheduled for later.
     */
    public boolean isScheduledForLater() {
        return scheduledFor != null && OffsetDateTime.now().isBefore(scheduledFor);
    }
}
