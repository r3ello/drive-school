package com.bellgado.calendar.api.sse;

import com.bellgado.calendar.api.dto.SlotEventResponse;
import com.bellgado.calendar.api.dto.SlotResponse;
import com.bellgado.calendar.application.service.SlotEventService;
import com.bellgado.calendar.application.service.SlotService;
import com.bellgado.calendar.domain.entity.SlotEvent;
import com.bellgado.calendar.domain.enums.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
@Slf4j
public class SseController {

    /** Maximum number of missed events replayed on reconnect. */
    private static final int REPLAY_LIMIT = 200;

    /** Event types that have no SSE wire representation — skipped during replay. */
    private static final Set<EventType> SKIP_REPLAY = Set.of(EventType.NOTES_UPDATED);

    @Value("${sse.emitter.timeout-millis:300000}")
    private long emitterTimeoutMillis;

    private final SseEmitterRegistry registry;
    private final SlotEventService slotEventService;
    private final SlotService slotService;
    private final ObjectMapper objectMapper;

    @GetMapping(produces = "text/event-stream")
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

        UUID clientId = UUID.randomUUID();
        SseEmitter emitter = registry.register(clientId, emitterTimeoutMillis);

        // Replay missed events if client reconnects with Last-Event-ID
        if (lastEventId != null && !lastEventId.isBlank()) {
            replayMissedEvents(emitter, lastEventId);
        }

        log.debug("SSE stream opened for client {}", clientId);
        return emitter;
    }

    private void replayMissedEvents(SseEmitter emitter, String lastEventId) {
        OffsetDateTime since;
        try {
            since = OffsetDateTime.parse(lastEventId);
        } catch (DateTimeParseException e) {
            log.warn("Invalid Last-Event-ID '{}', skipping replay: {}", lastEventId, e.getMessage());
            return;
        }

        List<SlotEvent> missed = slotEventService.findAfter(since, REPLAY_LIMIT);
        log.debug("Replaying {} missed slot events since {}", missed.size(), since);

        for (SlotEvent event : missed) {
            if (SKIP_REPLAY.contains(event.getType())) {
                continue;
            }

            SseEventType sseType = mapToSseType(event.getType());
            if (sseType == null) {
                continue;
            }

            try {
                SlotResponse slotResponse = null;
                try {
                    slotResponse = slotService.getById(event.getSlotId());
                } catch (Exception ex) {
                    log.debug("Slot {} not found during replay, sending null slot", event.getSlotId());
                }

                SlotSsePayload payload = new SlotSsePayload(
                        sseType,
                        event.getAt(),
                        SlotEventResponse.from(event),
                        slotResponse
                );

                emitter.send(SseEmitter.event()
                        .id(event.getAt().toString())
                        .name(sseType.name())
                        .data(objectMapper.writeValueAsString(payload)));
            } catch (Exception ex) {
                log.warn("Failed to replay event {} to client: {}", event.getId(), ex.getMessage());
                return;
            }
        }
    }

    private SseEventType mapToSseType(EventType type) {
        return switch (type) {
            case CREATED -> SseEventType.SLOT_CREATED;
            case GENERATED -> SseEventType.SLOT_GENERATED;
            case BOOKED -> SseEventType.SLOT_BOOKED;
            case CANCELLED -> SseEventType.SLOT_CANCELLED;
            case FREED -> SseEventType.SLOT_FREED;
            case REPLACED -> SseEventType.SLOT_REPLACED;
            case RESCHEDULED -> SseEventType.SLOT_RESCHEDULED;
            case BLOCKED -> SseEventType.SLOT_BLOCKED;
            case UNBLOCKED -> SseEventType.SLOT_UNBLOCKED;
            default -> null;
        };
    }
}
