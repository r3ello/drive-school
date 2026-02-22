package com.bellgado.calendar.api.sse;

import com.bellgado.calendar.api.dto.SlotEventResponse;
import com.bellgado.calendar.api.dto.SlotResponse;

import java.time.OffsetDateTime;

public record SlotSsePayload(
        SseEventType eventType,
        OffsetDateTime timestamp,
        SlotEventResponse slotEvent,
        SlotResponse slot
) {}
