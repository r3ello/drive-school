package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.WaitlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistItem, UUID>, JpaSpecificationExecutor<WaitlistItem> {

    @Query("SELECT w FROM WaitlistItem w JOIN FETCH w.student WHERE w.id = :id")
    Optional<WaitlistItem> findByIdWithStudent(@Param("id") UUID id);
}
