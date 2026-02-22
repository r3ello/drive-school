package com.bellgado.calendar.api.sse;

import com.bellgado.calendar.api.dto.StudentResponse;

import java.time.OffsetDateTime;

public record StudentSsePayload(
        SseEventType eventType,
        OffsetDateTime timestamp,
        StudentResponse student
) {}
