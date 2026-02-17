package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.SlotEventResponse;
import com.bellgado.calendar.domain.entity.SlotEvent;
import com.bellgado.calendar.domain.enums.EventType;
import com.bellgado.calendar.infrastructure.repository.SlotEventRepository;
import com.bellgado.calendar.infrastructure.specification.SlotEventSpecifications;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotEventService {

    private final SlotEventRepository slotEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordEvent(UUID slotId, EventType type) {
        SlotEvent event = new SlotEvent(slotId, type);
        slotEventRepository.save(event);
    }

    @Transactional
    public void recordEvent(UUID slotId, EventType type, UUID oldStudentId, UUID newStudentId) {
        SlotEvent event = new SlotEvent(slotId, type);
        event.setOldStudentId(oldStudentId);
        event.setNewStudentId(newStudentId);
        slotEventRepository.save(event);
    }

    @Transactional
    public void recordEvent(UUID slotId, EventType type, Map<String, Object> meta) {
        SlotEvent event = new SlotEvent(slotId, type);
        if (meta != null && !meta.isEmpty()) {
            try {
                event.setMeta(objectMapper.writeValueAsString(meta));
            } catch (JsonProcessingException e) {
                event.setMeta(meta.toString());
            }
        }
        slotEventRepository.save(event);
    }

    @Transactional
    public void recordEventWithStudents(UUID slotId, EventType type, UUID oldStudentId, UUID newStudentId, Map<String, Object> meta) {
        SlotEvent event = new SlotEvent(slotId, type);
        event.setOldStudentId(oldStudentId);
        event.setNewStudentId(newStudentId);
        if (meta != null && !meta.isEmpty()) {
            try {
                event.setMeta(objectMapper.writeValueAsString(meta));
            } catch (JsonProcessingException e) {
                event.setMeta(meta.toString());
            }
        }
        slotEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<SlotEventResponse> getBySlotId(UUID slotId) {
        return slotEventRepository.findBySlotIdOrderByAtDesc(slotId).stream()
                .map(SlotEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SlotEventResponse> list(OffsetDateTime from, OffsetDateTime to, Collection<EventType> types, Pageable pageable) {
        return slotEventRepository.findAll(
                SlotEventSpecifications.inDateRangeWithTypes(from, to, types),
                pageable
        ).map(SlotEventResponse::from);
    }
}
