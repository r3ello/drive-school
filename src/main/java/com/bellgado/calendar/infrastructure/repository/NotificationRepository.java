package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.Notification;
import com.bellgado.calendar.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    /**
     * Find notifications for a specific student.
     */
    Page<Notification> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    /**
     * Find notifications by status.
     */
    Page<Notification> findByStatusInOrderByCreatedAtDesc(Collection<NotificationStatus> statuses, Pageable pageable);

    /**
     * Find pending notifications ready for processing.
     * Uses SELECT FOR UPDATE SKIP LOCKED for safe concurrent processing.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = :status
        AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)
        AND (n.scheduledFor IS NULL OR n.scheduledFor <= :now)
        AND (n.expiresAt IS NULL OR n.expiresAt > :now)
        ORDER BY n.priority DESC, n.nextAttemptAt ASC
        """)
    List<Notification> findReadyForProcessing(
            @Param("status") NotificationStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    /**
     * Count notifications by status.
     */
    long countByStatus(NotificationStatus status);

    /**
     * Count notifications for a student.
     */
    long countByStudentId(UUID studentId);

    /**
     * Find expired pending notifications.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status IN :statuses
        AND n.expiresAt IS NOT NULL
        AND n.expiresAt < :now
        """)
    List<Notification> findExpired(
            @Param("statuses") Collection<NotificationStatus> statuses,
            @Param("now") OffsetDateTime now
    );

    /**
     * Bulk update status for expired notifications.
     */
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.status = :newStatus, n.updatedAt = :now
        WHERE n.status IN :fromStatuses
        AND n.expiresAt IS NOT NULL
        AND n.expiresAt < :now
        """)
    int markExpired(
            @Param("fromStatuses") Collection<NotificationStatus> fromStatuses,
            @Param("newStatus") NotificationStatus newStatus,
            @Param("now") OffsetDateTime now
    );

    /**
     * Find notifications by student and type within a time range.
     * Useful for preventing duplicate notifications.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.studentId = :studentId
        AND n.type = :type
        AND n.createdAt >= :since
        AND n.status NOT IN :excludeStatuses
        """)
    List<Notification> findRecentByStudentAndType(
            @Param("studentId") UUID studentId,
            @Param("type") com.bellgado.calendar.domain.enums.NotificationType type,
            @Param("since") OffsetDateTime since,
            @Param("excludeStatuses") Collection<NotificationStatus> excludeStatuses
    );
}
