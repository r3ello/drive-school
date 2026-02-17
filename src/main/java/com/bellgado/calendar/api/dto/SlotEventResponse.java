package com.bellgado.calendar.api.dto;

import com.bellgado.calendar.domain.entity.SlotEvent;
import com.bellgado.calendar.domain.enums.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record SlotEventResponse(
        UUID id,
        UUID slotId,
        EventType type,
        OffsetDateTime at,
        UUID oldStudentId,
        UUID newStudentId,
        Map<String, Object> meta
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static SlotEventResponse from(SlotEvent event) {
        Map<String, Object> metaMap = null;
        if (event.getMeta() != null && !event.getMeta().isEmpty()) {
            try {
                metaMap = objectMapper.readValue(event.getMeta(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                metaMap = Map.of("raw", event.getMeta());
            }
        }
        return new SlotEventResponse(
                event.getId(),
                event.getSlotId(),
                event.getType(),
                event.getAt(),
                event.getOldStudentId(),
                event.getNewStudentId(),
                metaMap
        );
    }
}
