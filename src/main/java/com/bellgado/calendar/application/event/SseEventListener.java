package com.bellgado.calendar.application.event;

import com.bellgado.calendar.api.sse.SseEmitterRegistry;
import com.bellgado.calendar.api.sse.SlotSsePayload;
import com.bellgado.calendar.api.sse.StudentSsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEventListener {

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Sofia");

    private final SseEmitterRegistry registry;

    @Async("sseEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotChanged(SlotChangedEvent event) {
        String eventId = event.slotEvent() != null && event.slotEvent().at() != null
                ? event.slotEvent().at().toString()
                : null;

        SlotSsePayload payload = new SlotSsePayload(
                event.eventType(),
                OffsetDateTime.now(APP_ZONE),
                event.slotEvent(),
                event.slot()
        );

        log.debug("Broadcasting SSE slot event: {} (id={})", event.eventType(), eventId);
        registry.broadcast(event.eventType(), eventId, payload);
    }

    @Async("sseEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudentChanged(StudentChangedEvent event) {
        StudentSsePayload payload = new StudentSsePayload(
                event.eventType(),
                OffsetDateTime.now(APP_ZONE),
                event.student()
        );

        log.debug("Broadcasting SSE student event: {}", event.eventType());
        registry.broadcast(event.eventType(), null, payload);
    }
}
