package com.muscledia.workout_service.event.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscledia.workout_service.model.EventOutbox;
import com.muscledia.workout_service.service.EventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Single Responsibility: Publishes events from outbox to Kafka
 * Open/Closed: Easy to add new event types without modification
 * Dependency Inversion: Depends on abstractions
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventOutboxPublisherScheduler {

    private final EventOutboxService eventOutboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final long FIXED_DELAY_MS = 5000; // Poll every 5 seconds
    private static final long CLEANUP_DELAY_MS = 3600000; // Clean up every hour

    @Scheduled(fixedDelay = FIXED_DELAY_MS)
    public void publishPendingEvents() {
        log.debug("Checking for pending events...");

        eventOutboxService.getPendingEvents()
                .count()
                .filter(count -> count > 0)
                .doOnNext(count -> log.info("Found {} pending outbox events", count))
                .flatMapMany(count -> eventOutboxService.getPendingEvents())
                .concatWith(eventOutboxService.getRetryableFailedEvents())
                .distinct(EventOutbox::getId)
                .filterWhen(event -> {
                    log.info("Marking as processing: eventId={}", event.getEventId());
                    return eventOutboxService.markAsProcessing(event.getId());
                })
                .flatMap(this::processEventWithRetry)
                .subscribe();
    }

    private Mono<Void> processEventWithRetry(EventOutbox event) {
        return deserializeEvent(event)
                .flatMap(eventPayload -> sendToKafkaWithRetry(event, eventPayload))
                .flatMap(result -> eventOutboxService.markAsPublished(event.getId()))
                .doOnSuccess(v -> log.info("Event successfully published: eventId={}", event.getEventId()))
                .onErrorResume(error -> {
                    log.error("Event processing failed: eventId={}, error={}",
                            event.getEventId(), error.getMessage());
                    return eventOutboxService.markAsFailed(event.getId(),
                            "Processing error: " + error.getMessage());
                })
                .then();
    }

    private Mono<Object> deserializeEvent(EventOutbox event) {
        return Mono.fromCallable(() -> {
            log.info("Deserializing event: eventId={}, type={}", event.getEventId(), event.getEventType());

            try {
                String className = getEventClassPath(event.getEventType());
                log.info("Using class: {}", className);
                Class<?> eventClass = Class.forName(className);
                Object deserializedEvent = objectMapper.readValue(event.getPayload(), eventClass);
                log.info("Deserialization success: eventId={}, class={}",
                        event.getEventId(), deserializedEvent.getClass().getSimpleName());
                return deserializedEvent;
            } catch (Exception e) {
                log.error("Deserialization failed: eventId={}, error={}", event.getEventId(), e.getMessage());
                throw new RuntimeException("Failed to deserialize event: " + e.getMessage(), e);
            }
        });
    }

    private Mono<org.springframework.kafka.support.SendResult<String, Object>> sendToKafkaWithRetry(
            EventOutbox event, Object eventPayload) {
        log.info("Sending to Kafka: eventId={}, topic={}, key={}",
                event.getEventId(), event.getTopic(), event.getMessageKey());

        return Mono.fromCallable(() -> {
                    try {
                        log.debug("Testing Kafka producer creation...");
                        kafkaTemplate.getProducerFactory().createProducer();
                        log.debug("Kafka producer test successful");
                        return true;
                    } catch (Exception e) {
                        log.error("Kafka producer creation failed: {}", e.getMessage());
                        throw new RuntimeException("Failed to construct kafka producer: " + e.getMessage(), e);
                    }
                })
                .flatMap(success -> {
                    return Mono.fromFuture(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), eventPayload)
                                    .toCompletableFuture())
                            .timeout(Duration.ofSeconds(10))
                            .doOnNext(result -> log.info("Kafka send success: eventId={}, offset={}",
                                    event.getEventId(), result.getRecordMetadata().offset()))
                            .doOnError(error -> log.error("Kafka send failed: eventId={}, error={}",
                                    event.getEventId(), error.getMessage()));
                })
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof RuntimeException)
                        .doBeforeRetry(retrySignal -> log.warn("Retrying Kafka send: attempt {}",
                                retrySignal.totalRetries() + 1)));
    }

    @Scheduled(fixedRate = CLEANUP_DELAY_MS)
    public void cleanupOldEvents() {
        log.info("Initiating outbox cleanup for published events older than 7 days...");
        eventOutboxService.cleanupOldEvents()
                .doOnSuccess(v -> log.info("Outbox cleanup completed"))
                .doOnError(e -> log.error("Outbox cleanup error: {}", e.getMessage(), e))
                .subscribe();
    }

    /**
     * Event class mapping following Open/Closed Principle
     * Easy to extend with new event types
     */
    private String getEventClassPath(String eventType) throws ClassNotFoundException {
        String className = switch (eventType) {
            case "WORKOUT_COMPLETED" -> "com.muscledia.workout_service.event.WorkoutCompletedEvent";
            case "PERSONAL_RECORD" -> "com.muscledia.workout_service.event.PersonalRecordEvent"; // Added PR support
            case "EXERCISE_COMPLETED" -> "com.muscledia.workout_service.event.ExerciseCompletedEvent";
            default -> throw new ClassNotFoundException("Unknown event type for class mapping: " + eventType);
        };
        log.debug("Event type '{}' mapped to class: {}", eventType, className);
        return className;
    }
}