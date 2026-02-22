package com.bellgado.calendar.application.event;

import com.bellgado.calendar.api.dto.SlotEventResponse;
import com.bellgado.calendar.api.dto.SlotResponse;
import com.bellgado.calendar.api.sse.SseEventType;

public record SlotChangedEvent(
        SseEventType eventType,
        SlotResponse slot,
        SlotEventResponse slotEvent
) {}
