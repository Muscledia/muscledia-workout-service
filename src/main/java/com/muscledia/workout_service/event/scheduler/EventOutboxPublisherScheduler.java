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

import java.time.Duration;

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
        log.info("🔄 POLLING FOR PENDING OUTBOX EVENTS...");

        Flux<EventOutbox> eventsToProcess = Flux.concat(
                        eventOutboxService.getPendingEvents()
                                .doOnNext(event -> log.info("📨 FOUND PENDING EVENT: id={}, eventId={}, type={}, topic={}, status={}",
                                        event.getId(), event.getEventId(), event.getEventType(), event.getTopic(), event.getStatus())),
                        eventOutboxService.getRetryableFailedEvents()
                                .doOnNext(event -> log.info("🔄 FOUND FAILED EVENT FOR RETRY: id={}, eventId={}, attempts={}",
                                        event.getId(), event.getEventId(), event.getAttemptCount()))
                )
                .distinct(EventOutbox::getId)
                .filterWhen(event -> {
                    log.info("🔒 ATTEMPTING TO MARK AS PROCESSING: eventId={}", event.getEventId());
                    return eventOutboxService.markAsProcessing(event.getId())
                            .doOnNext(marked -> log.info("🔒 MARK AS PROCESSING RESULT: eventId={}, marked={}", event.getEventId(), marked));
                });

        eventsToProcess
                .doOnNext(event -> log.info("📤 STARTING TO PROCESS EVENT: eventId={}, type={}, topic={}",
                        event.getEventId(), event.getEventType(), event.getTopic()))
                .flatMap(this::processEvent)
                .doOnComplete(() -> log.info("✅ FINISHED PROCESSING ALL EVENTS IN THIS CYCLE"))
                .subscribe(
                        null,
                        error -> log.error("❌ SCHEDULER STREAM ERROR: {}", error.getMessage(), error),
                        () -> log.debug("🔄 SCHEDULER CYCLE COMPLETED")
                );
    }

    private Mono<Void> processEvent(EventOutbox event) {
        return deserializeEvent(event)
                .flatMap(eventPayload -> sendToKafka(event, eventPayload))
                .flatMap(sendResult -> {
                    log.info("✅ KAFKA SEND SUCCESS - MARKING AS PUBLISHED: eventId={}", event.getEventId());
                    return eventOutboxService.markAsPublished(event.getId());
                })
                .doOnSuccess(v -> log.info("🎉 EVENT SUCCESSFULLY PUBLISHED: eventId={} to topic={}",
                        event.getEventId(), event.getTopic()))
                .onErrorResume(ex -> {
                    log.error("❌ EVENT PROCESSING FAILED: eventId={}, error={}", event.getEventId(), ex.getMessage());
                    return eventOutboxService.markAsFailed(event.getId(), "Processing error: " + ex.getMessage());
                })
                .then();
    }

    private Mono<Object> deserializeEvent(EventOutbox event) {
        return Mono.fromCallable(() -> {
            log.info("🔍 DESERIALIZING EVENT: eventId={}, type={}", event.getEventId(), event.getEventType());
            try {
                String className = getEventClassPath(event.getEventType());
                log.info("📋 USING CLASS: {}", className);
                Class<?> eventClass = Class.forName(className);
                Object deserializedEvent = objectMapper.readValue(event.getPayload(), eventClass);
                log.info("✅ DESERIALIZATION SUCCESS: eventId={}, class={}",
                        event.getEventId(), deserializedEvent.getClass().getSimpleName());
                return deserializedEvent;
            } catch (Exception e) {
                log.error("❌ DESERIALIZATION FAILED: eventId={}, error={}", event.getEventId(), e.getMessage());
                throw new RuntimeException("Failed to deserialize event: " + e.getMessage(), e);
            }
        });
    }

    private Mono<org.springframework.kafka.support.SendResult<String, Object>> sendToKafka(EventOutbox event, Object eventPayload) {
        log.info("📤 SENDING TO KAFKA: eventId={}, topic={}, key={}, payload={}",
                event.getEventId(), event.getTopic(), event.getMessageKey(), eventPayload.getClass().getSimpleName());

        return Mono.fromFuture(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), eventPayload).toCompletableFuture())
                .timeout(Duration.ofSeconds(10))
                .doOnNext(sendResult -> log.info("📨 KAFKA SEND RESULT: topic={}, partition={}, offset={}, timestamp={}",
                        sendResult.getRecordMetadata().topic(),
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset(),
                        sendResult.getRecordMetadata().timestamp()))
                .doOnError(error -> log.error("❌ KAFKA SEND FAILED: eventId={}, topic={}, error={}",
                        event.getEventId(), event.getTopic(), error.getMessage()));
    }

    @Scheduled(fixedRate = CLEANUP_DELAY_MS)
    public void cleanupOldEvents() {
        log.info("🧹 INITIATING OUTBOX CLEANUP for published events older than 7 days...");
        eventOutboxService.cleanupOldEvents()
                .doOnSuccess(v -> log.info("🧹 OUTBOX CLEANUP COMPLETED"))
                .doOnError(e -> log.error("❌ OUTBOX CLEANUP ERROR: {}", e.getMessage(), e))
                .subscribe();
    }

    // ✅ FIXED: Add all missing event types that appear in your logs
    private String getEventClassPath(String eventType) throws ClassNotFoundException {
        String className = switch (eventType) {
            case "WORKOUT_COMPLETED" -> "com.muscledia.workout_service.event.WorkoutCompletedEvent";
            default -> throw new ClassNotFoundException("Unknown event type for class mapping: " + eventType);
        };
        log.debug("📋 EVENT TYPE '{}' mapped to class: {}", eventType, className);
        return className;
    }
}
