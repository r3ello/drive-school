package com.bellgado.calendar.application.event;

import com.bellgado.calendar.api.dto.StudentResponse;
import com.bellgado.calendar.api.sse.SseEventType;

public record StudentChangedEvent(
        SseEventType eventType,
        StudentResponse student
) {}
