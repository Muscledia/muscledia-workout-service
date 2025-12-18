package com.muscledia.workout_service.event.publisher;

import com.muscledia.workout_service.event.PersonalRecordEvent;
import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.service.EventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes domain events to the outbox table within the current transaction.
 * This ensures that events are persisted atomically with business data changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionalEventPublisher {
    private final EventOutboxService eventOutboxService;

    /**
     * Publishes a WorkoutCompletedEvent to the outbox.
     * This method assumes it will be called within an existing reactive transaction.
     *
     * @param event The WorkoutCompletedEvent to publish.
     * @return Mono<Void> indicating completion.
     */
    public Mono<Void> publishWorkoutCompleted(WorkoutCompletedEvent event) {
        if (!event.isValid()) {
            log.error("Invalid WorkoutCompletedEvent: {}", event);
            return Mono.error(new IllegalArgumentException("Invalid WorkoutCompletedEvent provided."));
        }
        log.info("Attempting to publish WorkoutCompletedEvent for user {} and workout {}", event.getUserId(), event.getWorkoutId());
        // Use transactionalOperator to ensure storeForPublishing is part of the calling transaction
        return eventOutboxService.storeForPublishing(event)
                .doOnSuccess(saved -> log.info("WorkoutCompletedEvent for user {} and workout {} stored in outbox.", event.getUserId(), event.getWorkoutId()))
                .then(); // Convert to Mono<Void>
    }

    /**
     * Publishes a PersonalRecordEvent to the outbox.
     * This method assumes it will be called within an existing reactive transaction.
     *
     * @param event The PersonalRecordEvent to publish.
     * @return Mono<Void> indicating completion.
     */
    public Mono<Void> publishPersonalRecord(PersonalRecordEvent event) {
        if (!event.isValid()) {
            log.error("Invalid PersonalRecordEvent: {}", event);
            return Mono.error(new IllegalArgumentException("Invalid PersonalRecordEvent provided."));
        }
        log.info("Attempting to publish PersonalRecordEvent for user {} - {} {} {}",
                event.getUserId(), event.getNewValue(), event.getUnit(), event.getExerciseName());
        return eventOutboxService.storeForPublishing(event)
                .doOnSuccess(saved -> log.info("PersonalRecordEvent for user {} exercise '{}' stored in outbox.",
                        event.getUserId(), event.getExerciseName()))
                .then(); // Convert to Mono<Void>
    }

}
