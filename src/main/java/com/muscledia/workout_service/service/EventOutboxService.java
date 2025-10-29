package com.muscledia.workout_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscledia.workout_service.event.BaseEvent;
import com.muscledia.workout_service.model.EventOutbox;
import com.muscledia.workout_service.repository.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;


/**
 * Single Responsibility: Manages the Transactional Outbox Pattern
 * Open/Closed: Easy to extend with new event types
 * Dependency Inversion: Depends on abstractions (EventOutboxRepository, ObjectMapper)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventOutboxService {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Store an event for publishing (within a transaction)
     * Single method that handles all event types cleanly
     */
    public Mono<EventOutbox> storeForPublishing(BaseEvent event) {
        return Mono.fromCallable(() -> createOutboxEvent(event))
                .flatMap(outboxRepository::save)
                .doOnSuccess(saved -> log.info("✅ Stored {} event {} for user {} in outbox",
                        event.getEventType(), event.getEventId(), event.getUserId()))
                .doOnError(error -> log.error("❌ Failed to store {} event {}: {}",
                        event.getEventType(), event.getEventId(), error.getMessage()));
    }

    /**
     * Create EventOutbox entity from BaseEvent
     * Private method following Single Responsibility Principle
     */
    private EventOutbox createOutboxEvent(BaseEvent event) {
        try {
            if (!event.isValid()) {
                throw new IllegalArgumentException("Invalid event: " + event.getEventId());
            }

            String payload = objectMapper.writeValueAsString(event);
            String topic = determineTopicForEvent(event);

            return EventOutbox.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .topic(topic)
                    .messageKey(event.getUserId().toString())
                    .payload(payload)
                    .status(EventOutbox.EventStatus.PENDING)
                    .userId(event.getUserId())
                    .attemptCount(0)
                    .maxAttempts(3)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {} (type: {})", event.getEventId(), event.getEventType(), e);
            throw new RuntimeException("Event serialization failed for " + event.getEventType(), e);
        }
    }

    /**
     * Topic determination following Open/Closed Principle
     * Easy to extend with new event types without modifying existing code
     */
    private String determineTopicForEvent(BaseEvent event) {
        return switch (event.getEventType()) {
            case "WORKOUT_COMPLETED", "WORKOUT_STARTED" -> "workout-events";
            case "PERSONAL_RECORD" -> "personal-record-events";  // Match gamification service topic
            case "EXERCISE_COMPLETED", "SET_COMPLETED" -> "user-activity-events";
            case "PERSONAL_RECORD_ACHIEVED" -> "personal-record-events";  // Alternative event type
            default -> "general-events";
        };
    }

    public Flux<EventOutbox> getPendingEvents() {
        return outboxRepository.findPendingEvents();
    }

    public Flux<EventOutbox> getRetryableFailedEvents() {
        return outboxRepository.findRetryableFailedEvents(3, Instant.now());
    }

    public Mono<Void> markAsPublished(String outboxId) {
        return outboxRepository.findById(outboxId)
                .flatMap(event -> {
                    event.markAsPublished();
                    return outboxRepository.save(event);
                })
                .doOnSuccess(event -> log.debug("Marked event {} as published", event.getEventId()))
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Attempted to mark non-existent outbox event {} as published", outboxId)))
                .then();
    }

    public Mono<Void> markAsFailed(String outboxId, String errorMessage) {
        return outboxRepository.findById(outboxId)
                .flatMap(event -> {
                    event.markAsFailed(errorMessage);
                    return outboxRepository.save(event);
                })
                .doOnSuccess(event -> log.warn("Marked event {} as failed. Attempts: {}. Error: {}",
                        event.getEventId(), event.getAttemptCount(), errorMessage))
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Attempted to mark non-existent outbox event {} as failed", outboxId)))
                .then();
    }

    public Mono<Boolean> markAsProcessing(String outboxId) {
        return outboxRepository.findById(outboxId)
                .flatMap(event -> {
                    if (event.getStatus() == EventOutbox.EventStatus.PENDING ||
                            event.getStatus() == EventOutbox.EventStatus.FAILED) {
                        event.setStatus(EventOutbox.EventStatus.PROCESSING);
                        event.setUpdatedAt(Instant.now());
                        return outboxRepository.save(event).thenReturn(true);
                    } else {
                        log.info("Event {} already in status {}, skipping", outboxId, event.getStatus());
                        return Mono.just(false);
                    }
                })
                .switchIfEmpty(Mono.just(false));
    }

    public Mono<Boolean> eventExists(String eventId) {
        return outboxRepository.findByEventId(eventId).hasElement();
    }

    public Mono<OutboxStatistics> getStatistics() {
        return Mono.zip(
                outboxRepository.countByStatus(EventOutbox.EventStatus.PENDING),
                outboxRepository.countByStatus(EventOutbox.EventStatus.PROCESSING),
                outboxRepository.countByStatus(EventOutbox.EventStatus.PUBLISHED),
                outboxRepository.countByStatus(EventOutbox.EventStatus.FAILED),
                outboxRepository.countByStatus(EventOutbox.EventStatus.DEAD_LETTER)
        ).map(tuple -> OutboxStatistics.builder()
                .pendingCount(tuple.getT1())
                .processingCount(tuple.getT2())
                .publishedCount(tuple.getT3())
                .failedCount(tuple.getT4())
                .deadLetterCount(tuple.getT5())
                .build());
    }

    public Mono<Void> cleanupOldEvents() {
        Instant cutoffTime = Instant.now().minus(7, ChronoUnit.DAYS);
        return outboxRepository.findOldPublishedEvents(cutoffTime)
                .collectList()
                .flatMap(oldEvents -> {
                    if (!oldEvents.isEmpty()) {
                        return outboxRepository.deleteAll(oldEvents)
                                .doOnSuccess(v -> log.info("Cleaned up {} old published events", oldEvents.size()));
                    } else {
                        log.debug("No old published events to clean up");
                        return Mono.empty();
                    }
                })
                .then();
    }

    public Flux<EventOutbox> getDeadLetterEvents() {
        return outboxRepository.findDeadLetterEvents();
    }

    public Mono<Void> retryDeadLetterEvent(String outboxId) {
        return outboxRepository.findById(outboxId)
                .flatMap(event -> {
                    if (event.getStatus() == EventOutbox.EventStatus.DEAD_LETTER) {
                        event.setStatus(EventOutbox.EventStatus.PENDING);
                        event.setAttemptCount(0);
                        event.setErrorMessage(null);
                        event.setNextRetryAt(null);
                        event.setUpdatedAt(Instant.now());
                        return outboxRepository.save(event)
                                .doOnSuccess(saved -> log.info("Re-queued dead letter event {} for retry", event.getEventId()));
                    } else {
                        log.warn("Event {} is not in DEAD_LETTER status; cannot retry", outboxId);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Attempted to retry non-existent event {}", outboxId)))
                .then();
    }

    @lombok.Value
    @lombok.Builder
    public static class OutboxStatistics {
        long pendingCount;
        long processingCount;
        long publishedCount;
        long failedCount;
        long deadLetterCount;

        public long getTotalCount() {
            return pendingCount + processingCount + publishedCount + failedCount + deadLetterCount;
        }

        public double getSuccessRate() {
            long total = getTotalCount();
            return total > 0 ? (double) publishedCount / total * 100.0 : 0.0;
        }
    }
}