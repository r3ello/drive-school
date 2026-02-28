package com.bellgado.calendar.application.event;

import com.bellgado.calendar.api.dto.SlotEventResponse;
import com.bellgado.calendar.api.dto.SlotResponse;
import com.bellgado.calendar.api.dto.StudentBrief;
import com.bellgado.calendar.api.dto.StudentResponse;
import com.bellgado.calendar.api.sse.SseEmitterRegistry;
import com.bellgado.calendar.api.sse.SseEventType;
import com.bellgado.calendar.api.sse.SlotSsePayload;
import com.bellgado.calendar.api.sse.StudentSsePayload;
import com.bellgado.calendar.domain.enums.EventType;
import com.bellgado.calendar.domain.enums.SlotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseEventListenerTest {

    @Mock
    private SseEmitterRegistry registry;

    private SseEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new SseEventListener(registry);
    }

    // =========================================================================
    // onSlotChanged
    // =========================================================================

    @Test
    void onSlotChanged_shouldBroadcastWithCorrectEventType() {
        UUID slotId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        SlotEventResponse slotEvent = new SlotEventResponse(
                UUID.randomUUID(), slotId, EventType.BOOKED, now, null, studentId, null);
        SlotResponse slot = new SlotResponse(
                slotId, now, now.plusHours(1), SlotStatus.BOOKED, null, null, 1, now, now);

        SlotChangedEvent event = new SlotChangedEvent(SseEventType.SLOT_BOOKED, slot, slotEvent);

        listener.onSlotChanged(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> studentIdsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(registry).broadcast(eq(SseEventType.SLOT_BOOKED), eq(now.toString()),
                payloadCaptor.capture(), studentIdsCaptor.capture());

        SlotSsePayload payload = (SlotSsePayload) payloadCaptor.getValue();
        assertEquals(SseEventType.SLOT_BOOKED, payload.eventType());
        assertEquals(slotEvent, payload.slotEvent());
        assertEquals(slot, payload.slot());
        assertNotNull(payload.timestamp());
        assertTrue(studentIdsCaptor.getValue().contains(studentId));
    }

    @Test
    void onSlotChanged_shouldUseSlotEventAtAsEventId() {
        UUID slotId = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.parse("2026-02-20T14:30:00+02:00");

        SlotEventResponse slotEvent = new SlotEventResponse(
                UUID.randomUUID(), slotId, EventType.CREATED, at, null, null, null);
        SlotResponse slot = new SlotResponse(
                slotId, at, at.plusHours(1), SlotStatus.FREE, null, null, 0, at, at);

        SlotChangedEvent event = new SlotChangedEvent(SseEventType.SLOT_CREATED, slot, slotEvent);

        listener.onSlotChanged(event);

        verify(registry).broadcast(eq(SseEventType.SLOT_CREATED), eq(at.toString()), any(), any());
    }

    @Test
    void onSlotChanged_shouldHandleNullSlotEvent() {
        SlotChangedEvent event = new SlotChangedEvent(SseEventType.SLOT_CREATED, null, null);

        // Should not throw; eventId will be null
        assertDoesNotThrow(() -> listener.onSlotChanged(event));
        verify(registry).broadcast(eq(SseEventType.SLOT_CREATED), isNull(), any(), isNull());
    }

    // =========================================================================
    // onStudentChanged
    // =========================================================================

    @Test
    void onStudentChanged_shouldBroadcastWithCorrectEventType() {
        UUID studentId = UUID.randomUUID();
        StudentResponse student = new StudentResponse(
                studentId, "Jane Doe", null, null, null, true,
                null, false, null, false, null, null, null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());

        StudentChangedEvent event = new StudentChangedEvent(SseEventType.STUDENT_CREATED, student);

        listener.onStudentChanged(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> studentIdsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(registry).broadcast(eq(SseEventType.STUDENT_CREATED), isNull(),
                payloadCaptor.capture(), studentIdsCaptor.capture());

        StudentSsePayload payload = (StudentSsePayload) payloadCaptor.getValue();
        assertEquals(SseEventType.STUDENT_CREATED, payload.eventType());
        assertEquals(student, payload.student());
        assertNotNull(payload.timestamp());
        assertTrue(studentIdsCaptor.getValue().contains(studentId));
    }

    @Test
    void onStudentChanged_shouldBroadcastDeactivatedWithCorrectType() {
        UUID studentId = UUID.randomUUID();
        StudentResponse student = new StudentResponse(
                studentId, "John Doe", null, null, null, false,
                null, false, null, false, null, null, null, null, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());

        StudentChangedEvent event = new StudentChangedEvent(SseEventType.STUDENT_DEACTIVATED, student);

        listener.onStudentChanged(event);

        verify(registry).broadcast(eq(SseEventType.STUDENT_DEACTIVATED), isNull(),
                any(StudentSsePayload.class), any());
    }
}
