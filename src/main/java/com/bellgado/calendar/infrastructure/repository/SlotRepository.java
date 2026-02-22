package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SlotRepository extends JpaRepository<Slot, UUID>, JpaSpecificationExecutor<Slot> {

    @Query("SELECT s FROM Slot s LEFT JOIN FETCH s.student WHERE s.id = :id")
    Optional<Slot> findByIdWithStudent(@Param("id") UUID id);

    boolean existsByStartAt(OffsetDateTime startAt);

    @Query("SELECT s.startAt FROM Slot s WHERE s.startAt >= :from AND s.startAt < :to")
    Set<OffsetDateTime> findStartAtBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    List<Slot> findByBlockId(UUID blockId);
}
