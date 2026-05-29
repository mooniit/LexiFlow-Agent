package com.example.lexiflow.review.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ReviewEventBus {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long reviewId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.computeIfAbsent(reviewId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(reviewId, emitter));
        emitter.onTimeout(() -> remove(reviewId, emitter));
        publish(reviewId, "CONNECTED", "SSE stream connected.");
        return emitter;
    }

    public void publish(Long reviewId, String type, String message) {
        ReviewEvent event = new ReviewEvent(reviewId, type, message, OffsetDateTime.now());
        for (SseEmitter emitter : emitters.getOrDefault(reviewId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name(type).data(event));
            } catch (IOException ex) {
                remove(reviewId, emitter);
            }
        }
    }

    private void remove(Long reviewId, SseEmitter emitter) {
        List<SseEmitter> reviewEmitters = emitters.get(reviewId);
        if (reviewEmitters != null) {
            reviewEmitters.remove(emitter);
        }
    }

    public record ReviewEvent(Long reviewId, String type, String message, OffsetDateTime occurredAt) {
    }
}
