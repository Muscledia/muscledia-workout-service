package com.muscledia.workout_service.event.handler;


import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.repository.WorkoutRepository;
import com.muscledia.workout_service.service.analytics.PersonalRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * EVENT-DRIVEN PERSONAL RECORDS PROCESSING
 *
 * This handler decouples personal record processing from workout completion:
 * - WorkoutOrchestrator publishes WorkoutCompletedEvent
 * - This handler processes personal records asynchronously
 * - No direct coupling between domain and analytics services
 *
 * Benefits:
 * - Clean separation of concerns
 * - Asynchronous processing (doesn't block workout completion)
 * - Easy to disable/enable personal record processing
 * - Testable in isolation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkoutCompletedEventHandler {

    private final PersonalRecordService personalRecordService;
    private final WorkoutRepository workoutRepository;

    /**
     * Handle WorkoutCompletedEvent by processing personal records
     *
     * ASYNC: This runs in a separate thread so it doesn't block workout completion
     * FAULT TOLERANT: Errors here don't affect the workout completion flow
     */
    @EventListener
    @Async
    public void handleWorkoutCompleted(WorkoutCompletedEvent event) {
        log.info("🏆 Processing personal records for completed workout: {} (async)", event.getWorkoutId());

        try {
            // Fetch the completed workout to get exercise details
            workoutRepository.findById(event.getWorkoutId())
                    .flatMap(this::processPersonalRecords)
                    .doOnSuccess(v -> log.info("✅ Personal records processed successfully for workout: {}", event.getWorkoutId()))
                    .doOnError(error -> log.error("❌ Failed to process personal records for workout {}: {}",
                            event.getWorkoutId(), error.getMessage()))
                    .onErrorResume(error -> {
                        // Don't let PR processing errors affect anything else
                        log.warn("⚠️ Personal record processing failed for workout {}, continuing...", event.getWorkoutId());
                        return Mono.empty();
                    })
                    .subscribe(); // Fire and forget - asynchronous processing

        } catch (Exception e) {
            log.error("❌ Unexpected error in personal record event handling for workout {}: {}",
                    event.getWorkoutId(), e.getMessage(), e);
        }
    }

    /**
     * Process personal records for the completed workout
     */
    private Mono<Void> processPersonalRecords(Workout workout) {
        log.debug("🔍 Processing {} exercises for personal records in workout: {}",
                workout.getExercises() != null ? workout.getExercises().size() : 0, workout.getId());

        if (workout.getExercises() == null || workout.getExercises().isEmpty()) {
            log.debug("⚠️ No exercises found in workout {}, skipping personal record processing", workout.getId());
            return Mono.empty();
        }

        // Use the existing PersonalRecordService method
        return personalRecordService.processWorkoutForPersonalRecords(workout)
                .doOnSuccess(v -> log.debug("🏆 Completed personal record processing for workout: {}", workout.getId()));
    }
}
