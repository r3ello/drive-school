package com.bellgado.calendar.api.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEmitterRegistry {

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID clientId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.put(clientId, emitter);

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

        log.debug("SSE client registered: {} (total: {})", clientId, emitters.size());
        return emitter;
    }

    public void broadcast(SseEventType eventType, String eventId, Object payload) {
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

        // Snapshot to avoid ConcurrentModificationException if a client disconnects mid-broadcast
        List<Map.Entry<UUID, SseEmitter>> snapshot = List.copyOf(emitters.entrySet());

        for (Map.Entry<UUID, SseEmitter> entry : snapshot) {
            sendToEmitter(entry.getKey(), entry.getValue(), eventType, eventId, data);
        }
    }

    private void sendToEmitter(UUID clientId, SseEmitter emitter, SseEventType eventType, String eventId, String data) {
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
        List<Map.Entry<UUID, SseEmitter>> snapshot = List.copyOf(emitters.entrySet());
        for (Map.Entry<UUID, SseEmitter> entry : snapshot) {
            try {
                synchronized (entry.getValue()) {
                    entry.getValue().send(SseEmitter.event().comment("heartbeat"));
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
