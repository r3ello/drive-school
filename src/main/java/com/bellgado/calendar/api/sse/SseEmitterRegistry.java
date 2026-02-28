package com.bellgado.calendar.api.sse;

import com.bellgado.calendar.domain.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEmitterRegistry {

    private final ObjectMapper objectMapper;

    /**
     * Carries the identity of the connected client so we can filter events per role.
     * studentId is non-null only for STUDENT role.
     */
    public record EmitterMeta(UUID userId, UserRole role, UUID studentId) {
        public static EmitterMeta teacher(UUID userId) {
            return new EmitterMeta(userId, UserRole.TEACHER, null);
        }
        public static EmitterMeta admin(UUID userId) {
            return new EmitterMeta(userId, UserRole.ADMIN, null);
        }
        public static EmitterMeta student(UUID userId, UUID studentId) {
            return new EmitterMeta(userId, UserRole.STUDENT, studentId);
        }
        public boolean isStudent() {
            return role == UserRole.STUDENT;
        }
    }

    private record EmitterEntry(SseEmitter emitter, EmitterMeta meta) {}

    private final ConcurrentHashMap<UUID, EmitterEntry> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID clientId, long timeoutMillis, EmitterMeta meta) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.put(clientId, new EmitterEntry(emitter, meta));

        emitter.onCompletion(() -> {
            emitters.remove(clientId);
            log.debug("SSE client disconnected: {}", clientId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(clientId);
            log.debug("SSE client timed out: {}", clientId);
        });
        emitter.onError(ex -> {
            emitters.remove(clientId);
            log.debug("SSE client error [{}]: {}", clientId, ex.getMessage());
        });

        log.debug("SSE client registered: {} role={} (total: {})",
                clientId, meta.role(), emitters.size());
        return emitter;
    }

    /**
     * Broadcasts an SSE event.
     *
     * @param relevantStudentIds  Student UUIDs that should receive this event.
     *                            TEACHER/ADMIN emitters always receive the event.
     *                            STUDENT emitters only receive it if their studentId
     *                            is contained in this set.
     *                            Pass {@code null} to broadcast to everyone.
     */
    public void broadcast(SseEventType eventType, String eventId, Object payload,
                          Set<UUID> relevantStudentIds) {
        if (emitters.isEmpty()) {
            return;
        }

        String data;
        try {
            data = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("Failed to serialize SSE payload for event {}: {}", eventType, e.getMessage());
            return;
        }

        List<Map.Entry<UUID, EmitterEntry>> snapshot = List.copyOf(emitters.entrySet());

        for (Map.Entry<UUID, EmitterEntry> entry : snapshot) {
            EmitterEntry ee = entry.getValue();
            if (shouldSend(ee.meta(), relevantStudentIds)) {
                sendToEmitter(entry.getKey(), ee.emitter(), eventType, eventId, data);
            }
        }
    }

    private boolean shouldSend(EmitterMeta meta, Set<UUID> relevantStudentIds) {
        if (!meta.isStudent()) {
            return true; // TEACHER/ADMIN always receive
        }
        if (relevantStudentIds == null) {
            return true; // null = broadcast to all
        }
        return meta.studentId() != null && relevantStudentIds.contains(meta.studentId());
    }

    private void sendToEmitter(UUID clientId, SseEmitter emitter,
                               SseEventType eventType, String eventId, String data) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(eventType.name())
                    .data(data);
            if (eventId != null) {
                event.id(eventId);
            }
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("Removing disconnected SSE client [{}]: {}", clientId, e.getMessage());
            emitters.remove(clientId);
        }
    }

    @Scheduled(fixedDelayString = "${sse.heartbeat.interval-millis:25000}")
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, EmitterEntry>> snapshot = List.copyOf(emitters.entrySet());
        for (Map.Entry<UUID, EmitterEntry> entry : snapshot) {
            try {
                synchronized (entry.getValue().emitter()) {
                    entry.getValue().emitter().send(SseEmitter.event().comment("heartbeat"));
                }
            } catch (IOException | IllegalStateException e) {
                emitters.remove(entry.getKey());
            }
        }
        log.trace("SSE heartbeat sent to {} client(s)", snapshot.size());
    }

    public int activeConnections() {
        return emitters.size();
    }
}
