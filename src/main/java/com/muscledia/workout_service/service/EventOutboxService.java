package com.muscledia.workout_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscledia.workout_service.event.BaseEvent;
import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.model.EventOutbox;
import com.muscledia.workout_service.repository.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


/**
 * Service for managing the Transactional Outbox Pattern in the Workout Service.
 *
 * This service ensures atomic event publishing by:
 * 1. Storing events in the database within the same transaction as business logic
 * 2. Having a separate process publish events from the outbox to Kafka
 * 3. Handling retries and dead letter events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventOutboxService {

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactionalOperator; // Inject for reactive transactions
    private final EventOutboxRepository eventOutboxRepository;

    // Define constants for Kafka topics
    public static final String WORKOUT_EVENTS_TOPIC = "workout-events";
    //public static final String USER_ACTIVITY_EVENTS_TOPIC = "user-activity-events";

    /**
     * Store an event for publishing (within a transaction)
     * This method should be called within the same reactive transaction as business logic.
     *
     * @param event The BaseEvent to be stored.
     * @return Mono<EventOutbox> representing the saved outbox entry.
     */
    public Mono<EventOutbox> storeForPublishing(BaseEvent event) {
        return Mono.fromCallable(() -> {
                    try {
                        String topic = determineTopicForEvent(event);
                        String messageKey = event.getUserId().toString();
                        return new EventOutbox(topic, messageKey, event, objectMapper);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize event {} (type: {}) for outbox storage",
                                event.getEventId(), event.getEventType(), e);
                        throw new RuntimeException("Event serialization failed for " + event.getEventType(), e);
                    }
                })
                .flatMap(outboxRepository::save) // Save reactively
                .doOnSuccess(savedEntry -> log.debug("Stored event {} (type: {}) for publishing to topic {}",
                        savedEntry.getEventId(), savedEntry.getEventType(), savedEntry.getTopic()))
                .doOnError(e -> log.error("Failed to store event {} (type: {}) in outbox: {}",
                        event.getEventId(), event.getEventType(), e.getMessage(), e));
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
                .then(); // Convert to Mono<Void>
    }

    public Mono<Void> markAsFailed(String outboxId, String errorMessage) {
        return outboxRepository.findById(outboxId)
                .flatMap(event -> {
                    event.markAsFailed(errorMessage);
                    return outboxRepository.save(event);
                })
                .doOnSuccess(event -> log.warn("Marked event {} (type: {}) as failed. Status: {}. Attempts: {}. Error: {}",
                        event.getEventId(), event.getEventType(), event.getStatus(), event.getAttemptCount(), errorMessage))
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Attempted to mark non-existent outbox event {} as failed", outboxId)))
                .then(); // Convert to Mono<Void>
    }

    public Mono<Boolean> markAsProcessing(String outboxId) {
        return outboxRepository.findById(outboxId)
                .flatMap(event -> {
                    if (event.getStatus() == EventOutbox.EventStatus.PENDING || event.getStatus() == EventOutbox.EventStatus.FAILED) {
                        event.setStatus(EventOutbox.EventStatus.PROCESSING);
                        event.setUpdatedAt(Instant.now());
                        return outboxRepository.save(event).thenReturn(true);
                    } else {
                        log.info("Outbox event {} is already in status {}, skipping mark as processing.", outboxId, event.getStatus());
                        return Mono.just(false);
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Attempted to mark non-existent outbox event {} as processing", outboxId))
                        .thenReturn(false));
    }

    public Mono<Boolean> eventExists(String eventId) {
        return outboxRepository.findByEventId(eventId).hasElement(); // Reactive way to check existence
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
                .collectList() // Collect Flux into a List
                .flatMap(oldEvents -> {
                    if (!oldEvents.isEmpty()) {
                        return outboxRepository.deleteAll(oldEvents) // Reactive deleteAll
                                .doOnSuccess(v -> log.info("Cleaned up {} old published events from workout outbox", oldEvents.size()));
                    } else {
                        log.debug("No old published events to clean up in workout outbox.");
                        return Mono.empty();
                    }
                })
                .then(); // Convert to Mono<Void>
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
                                .doOnSuccess(saved -> log.info("Manually re-queued dead letter event {} (type: {}) for retry.", event.getEventId(), event.getEventType()));
                    } else {
                        log.warn("Event {} is not in DEAD_LETTER status; cannot retry manually.", outboxId);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("Attempted to retry non-existent outbox event {}.", outboxId)))
                .then();
    }

    private String determineTopicForEvent(BaseEvent event) {
        if (event instanceof WorkoutCompletedEvent) {
            log.debug("WorkoutCompletedEvent → topic: {}", WORKOUT_EVENTS_TOPIC);
            return WORKOUT_EVENTS_TOPIC;
        }
        log.error("Unknown event type: {} encountered for topic determination.", event.getEventType());
        throw new IllegalArgumentException("Cannot determine Kafka topic for unknown event type: " + event.getEventType());
    }

    public Mono<EventOutbox> saveEvent(BaseEvent event) {
        return Mono.fromCallable(() -> {
                    try {
                        // Validate the event
                        if (!event.isValid()) {
                            throw new IllegalArgumentException("Invalid event: " + event.getEventId());
                        }

                        // Serialize the event to JSON
                        String payload = objectMapper.writeValueAsString(event);

                        // Determine topic based on event type
                        String topic = getTopicForEventType(event.getEventType());

                        // Create EventOutbox entity
                        EventOutbox outboxEvent = EventOutbox.builder()
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

                        log.info("💾 SAVING EVENT TO OUTBOX: eventId={}, type={}, topic={}, userId={}",
                                event.getEventId(), event.getEventType(), topic, event.getUserId());

                        return outboxEvent;
                    } catch (Exception e) {
                        log.error("❌ FAILED TO PREPARE EVENT FOR OUTBOX: eventId={}, error={}",
                                event.getEventId(), e.getMessage());
                        throw new RuntimeException("Failed to serialize event for outbox", e);
                    }
                })
                .flatMap(eventOutboxRepository::save)
                .doOnSuccess(saved -> log.info("✅ EVENT SAVED TO OUTBOX: id={}, eventId={}, status={}",
                        saved.getId(), saved.getEventId(), saved.getStatus()))
                .doOnError(error -> log.error("❌ FAILED TO SAVE EVENT TO OUTBOX: {}", error.getMessage()));
    }

    // ✅ IMPLEMENT: Topic mapping based on event type
    private String getTopicForEventType(String eventType) {
        return switch (eventType) {
            case "WORKOUT_COMPLETED", "WORKOUT_STARTED" -> "workout-events";
            case "EXERCISE_COMPLETED", "SET_COMPLETED" -> "user-activity-events";
            case "PERSONAL_RECORD_ACHIEVED" -> "achievement-events";
            default -> "general-events";
        };
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
