package com.muscledia.workout_service.event.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscledia.workout_service.model.EventOutbox;
import com.muscledia.workout_service.service.EventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Scheduled task that periodically polls the event outbox table
 * and publishes PENDING and FAILED (retryable) events to Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventOutboxPublisherScheduler {
    private final EventOutboxService eventOutboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper; // To deserialize payload back to object for KafkaTemplate

    // Constants for scheduling (can be moved to application.yml)
    private static final long FIXED_DELAY_MS = 5000; // Poll every 5 seconds
    private static final long CLEANUP_DELAY_MS = 3600000; // Clean up every hour

    /**
     * Scheduled task to find and publish pending events.
     * Runs every `fixedDelay` milliseconds after the previous one finishes.
     */
    @Scheduled(fixedDelay = FIXED_DELAY_MS)
    public void publishPendingEvents() {
        log.debug("Polling for pending outbox events...");

        Flux<EventOutbox> eventsToProcess = Flux.concat(
                        eventOutboxService.getPendingEvents(),
                        eventOutboxService.getRetryableFailedEvents()
                ).distinct(EventOutbox::getId) // Ensure unique events if they could appear in both lists
                .filterWhen(event -> eventOutboxService.markAsProcessing(event.getId())); // Mark as processing reactively

        eventsToProcess
                .flatMap(event -> {
                    return Mono.fromCallable(() -> {
                                // Deserialize payload to the actual event object
                                return objectMapper.readValue(event.getPayload(), Class.forName(getEventClassPath(event.getEventType())));
                            })
                            .flatMap(eventPayload -> {
                                log.debug("Attempting to send event {} (type: {}) to topic {}", event.getEventId(), event.getEventType(), event.getTopic());
                                // Send to Kafka, then handle success/failure
                                return Mono.fromFuture(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), eventPayload).toCompletableFuture())
                                        .flatMap(sendResult -> eventOutboxService.markAsPublished(event.getId()))
                                        .doOnSuccess(v -> log.info("Successfully published event {} (type: {}) to topic {}",
                                                event.getEventId(), event.getEventType(), event.getTopic()))
                                        // The 'ex.getMessage()' should work here. If not, it's an environment/IDE issue.
                                        .onErrorResume(ex -> {
                                            log.error("Failed to publish event {} (type: {}) to topic {}: {}",
                                                    event.getEventId(), event.getEventType(), event.getTopic(), ex.getMessage(), ex);
                                            return eventOutboxService.markAsFailed(event.getId(), "Kafka publish error: " + ex.getMessage());
                                        })
                                        .then(); // Convert to Mono<Void>
                            })
                            .onErrorResume(e -> {
                                log.error("Error processing event {} from outbox before sending to Kafka: {}",
                                        event.getEventId(), e.getMessage(), e);
                                return eventOutboxService.markAsFailed(event.getId(), "Serialization/Processing error: " + e.getMessage());
                            });
                })
                .subscribe(null, // onNext: no-op since we return Mono<Void>
                        error -> log.error("Error in event outbox publisher scheduler stream: {}", error.getMessage(), error),
                        () -> log.debug("Finished polling and processing outbox events for this cycle."));
    }

    /**
     * Scheduled task to clean up old published events from the outbox.
     */
    @Scheduled(fixedRate = CLEANUP_DELAY_MS)
    public void cleanupOldEvents() {
        log.info("Initiating outbox cleanup for published events older than 7 days...");
        eventOutboxService.cleanupOldEvents()
                .doOnSuccess(v -> log.info("Outbox cleanup completed."))
                .doOnError(e -> log.error("Error during outbox cleanup: {}", e.getMessage(), e))
                .subscribe(); // Subscribe to trigger the reactive chain
    }

    /**
     * Helper to get the fully qualified class name based on eventType.
     */
    private String getEventClassPath(String eventType) throws ClassNotFoundException {
        return switch (eventType) {
            case "WORKOUT_COMPLETED" -> "com.muscledia.workout_service.event.WorkoutCompletedEvent";
            case "EXERCISE_COMPLETED" -> "com.muscledia.workout_service.event.ExerciseCompletedEvent";
            default -> throw new ClassNotFoundException("Unknown event type for class mapping: " + eventType);
        };
    }
}
