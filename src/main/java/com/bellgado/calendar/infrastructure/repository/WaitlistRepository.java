package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.WaitlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistItem, UUID>, JpaSpecificationExecutor<WaitlistItem> {

    @Query("SELECT w FROM WaitlistItem w JOIN FETCH w.student WHERE w.id = :id")
    Optional<WaitlistItem> findByIdWithStudent(@Param("id") UUID id);

    /** Soft-delete all active waitlist entries for a student in one query. */
    @Modifying
    @Query("UPDATE WaitlistItem w SET w.active = false WHERE w.student.id = :studentId AND w.active = true")
    int deactivateAllActiveByStudentId(@Param("studentId") UUID studentId);
}
