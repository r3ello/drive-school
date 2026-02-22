package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.SlotEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SlotEventRepository extends JpaRepository<SlotEvent, UUID>, JpaSpecificationExecutor<SlotEvent> {

    List<SlotEvent> findBySlotIdOrderByAtDesc(UUID slotId);

    List<SlotEvent> findByAtAfterOrderByAtAsc(OffsetDateTime since, Pageable pageable);
}
