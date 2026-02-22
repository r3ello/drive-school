package com.bellgado.calendar.api.sse;

/**
 * SSE wire-format event names.
 * NOTES_UPDATED is intentionally absent — it is an internal-only event type.
 */
public enum SseEventType {
    SLOT_CREATED,
    SLOT_GENERATED,
    SLOT_BOOKED,
    SLOT_CANCELLED,
    SLOT_FREED,
    SLOT_REPLACED,
    SLOT_RESCHEDULED,
    SLOT_BLOCKED,
    SLOT_UNBLOCKED,
    STUDENT_CREATED,
    STUDENT_UPDATED,
    STUDENT_DEACTIVATED,
    HEARTBEAT
}
