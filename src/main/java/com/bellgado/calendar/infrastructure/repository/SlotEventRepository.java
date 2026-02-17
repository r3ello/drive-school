package com.bellgado.calendar.infrastructure.repository;

import com.bellgado.calendar.domain.entity.SlotEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SlotEventRepository extends JpaRepository<SlotEvent, UUID>, JpaSpecificationExecutor<SlotEvent> {

    List<SlotEvent> findBySlotIdOrderByAtDesc(UUID slotId);
}
