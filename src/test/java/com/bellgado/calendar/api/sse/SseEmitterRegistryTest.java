package com.bellgado.calendar.api.sse;

import com.bellgado.calendar.api.sse.SseEmitterRegistry.EmitterMeta;
import com.bellgado.calendar.domain.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;
    private static final EmitterMeta TEACHER_META =
            new EmitterMeta(UUID.randomUUID(), UserRole.TEACHER, null);

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(new ObjectMapper());
    }

    @Test
    void register_shouldReturnEmitterAndTrackConnection() {
        UUID clientId = UUID.randomUUID();

        SseEmitter emitter = registry.register(clientId, 30_000L, TEACHER_META);

        assertNotNull(emitter);
        assertEquals(1, registry.activeConnections());
    }

    @Test
    void register_shouldRemoveEmitterOnNextBroadcastAfterCompletion() {
        // In a unit-test environment (no servlet container), emitter.complete() marks the
        // emitter as done internally but does NOT fire the Spring MVC onCompletion callback.
        // The registry removes it on the next broadcast when send() throws IllegalStateException.
        UUID clientId = UUID.randomUUID();
        SseEmitter emitter = registry.register(clientId, 30_000L, TEACHER_META);
        assertEquals(1, registry.activeConnections());

        emitter.complete();

        // broadcast triggers send() which throws IllegalStateException → registry removes it
        registry.broadcast(SseEventType.HEARTBEAT, null, "ping", null);

        assertEquals(0, registry.activeConnections());
    }

    @Test
    void broadcast_shouldShortCircuitWhenNoClients() {
        // Should not throw even with no registered clients
        assertDoesNotThrow(() ->
                registry.broadcast(SseEventType.SLOT_CREATED, "some-id", "payload", null));
        assertEquals(0, registry.activeConnections());
    }

    @Test
    void broadcast_shouldRemoveCompletedEmitterOnSendFailure() {
        UUID clientId = UUID.randomUUID();
        SseEmitter emitter = registry.register(clientId, 30_000L, TEACHER_META);
        assertEquals(1, registry.activeConnections());

        // Complete the emitter — next send() will throw IllegalStateException
        emitter.complete();
        registry.broadcast(SseEventType.HEARTBEAT, null, "ping", null);

        assertEquals(0, registry.activeConnections());
    }

    @Test
    void activeConnections_shouldReflectMultipleRegistrations() {
        registry.register(UUID.randomUUID(), 30_000L, TEACHER_META);
        registry.register(UUID.randomUUID(), 30_000L, TEACHER_META);
        registry.register(UUID.randomUUID(), 30_000L, TEACHER_META);

        assertEquals(3, registry.activeConnections());
    }

    @Test
    void activeConnections_shouldDecreaseAfterBroadcastToCompletedEmitter() {
        UUID clientId = UUID.randomUUID();
        SseEmitter emitter = registry.register(clientId, 30_000L, TEACHER_META);
        assertEquals(1, registry.activeConnections());

        emitter.complete();
        // Broadcasting triggers the removal of the stale emitter
        registry.broadcast(SseEventType.SLOT_BOOKED, "evt-id", "data", null);

        assertEquals(0, registry.activeConnections());
    }
}
