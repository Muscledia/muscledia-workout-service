package com.muscledia.workout_service.domain.service;

import com.muscledia.workout_service.domain.model.WorkoutData;
import com.muscledia.workout_service.domain.vo.WorkoutMetrics;
import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
import com.muscledia.workout_service.exception.InvalidWorkoutStateException;
import com.muscledia.workout_service.exception.WorkoutNotFoundException;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import com.muscledia.workout_service.repository.WorkoutRepository;
import com.muscledia.workout_service.service.analytics.PersonalRecordService;
import com.muscledia.workout_service.validation.WorkoutValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutOrchestrator {
    // Domain Services
    private final WorkoutValidationService validationService;
    private final WorkoutMetricsCalculator metricsCalculator;
    private final WorkoutDomainMapper domainMapper;

    // Infrastructure Services
    private final WorkoutRepository repository;
    private final TransactionalEventPublisher eventPublisher;
    private final PersonalRecordService personalRecordService;
    private final TransactionalOperator transactionalOperator;


    /**
     * Complete workout using DDD orchestration pattern
     */
    public Mono<Workout> completeWorkout(String workoutId, Long userId) {
        log.info("🎯 Starting workout completion orchestration for workoutId: {}, userId: {}", workoutId, userId);

        return transactionalOperator.execute(status ->
                repository.findByIdAndUserId(workoutId, userId)
                        .switchIfEmpty(Mono.error(new WorkoutNotFoundException("Workout not found: " + workoutId)))
                        .flatMap(this::validateAndComplete)
                        .flatMap(this::calculateAndPublishEvents)
                        .doOnSuccess(workout -> log.info("✅ Workout completion orchestration successful for: {}", workoutId))
                        .doOnError(error -> log.error("❌ Workout completion orchestration failed for {}: {}", workoutId, error.getMessage()))
        ).single();
    }

    /**
     * Complete workout with additional completion data
     */
    public Mono<Workout> completeWorkout(String workoutId, Long userId, Map<String, Object> completionData) {
        log.info("🎯 Starting workout completion with data for workoutId: {}", workoutId);

        return transactionalOperator.execute(status ->
                repository.findByIdAndUserId(workoutId, userId)
                        .switchIfEmpty(Mono.error(new WorkoutNotFoundException("Workout not found: " + workoutId)))
                        .flatMap(workout -> validateAndComplete(workout, completionData))
                        .flatMap(this::calculateAndPublishEvents)
        ).single();
    }

    /**
     * Validate workout and mark as completed
     */
    private Mono<Workout> validateAndComplete(Workout workout) {
        return validateAndComplete(workout, null);
    }

    /**
     * Validate workout and mark as completed with additional data
     */
    private Mono<Workout> validateAndComplete(Workout workout, Map<String, Object> completionData) {
        log.debug("🔍 Validating workout {} for completion", workout.getId());

        // Step 1: Domain Validation
        var validationResult = validationService.validateForCompletion(workout);
        if (validationResult.isInvalid()) {
            log.warn("❌ Workout validation failed: {}", validationResult.getErrorMessage());
            return Mono.error(new InvalidWorkoutStateException(validationResult.getErrorMessage()));
        }

        // Step 2: Apply completion data if provided
        if (completionData != null) {
            applyCompletionData(workout, completionData);
        }

        // Step 3: Mark workout as completed (simple state change)
        workout.setStatus(WorkoutStatus.COMPLETED);
        workout.setCompletedAt(LocalDateTime.now());

        // Calculate duration if not set
        if (workout.getDurationMinutes() == null && workout.getStartedAt() != null) {
            Duration duration = Duration.between(workout.getStartedAt(), workout.getCompletedAt());
            workout.setDurationMinutes((int) duration.toMinutes());
        }

        // Step 4: Save to database
        return repository.save(workout)
                .doOnSuccess(saved -> log.info("💾 Workout {} marked as completed and saved", saved.getId()));
    }

    /**
     * Calculate metrics and publish completion events
     */
    private Mono<Workout> calculateAndPublishEvents(Workout completedWorkout) {
        log.debug("📊 Starting metrics calculation and event publishing for workout: {}", completedWorkout.getId());

        return Mono.fromCallable(() -> {
                    // Convert to domain object for pure domain calculations
                    WorkoutData workoutData = domainMapper.toDomainWorkout(completedWorkout);
                    return calculateMetricsUsingDomain(workoutData);
                })
                .flatMap(metrics -> publishWorkoutCompletedEvent(completedWorkout, metrics))
                .flatMap(event -> checkPersonalRecords(completedWorkout))
                .then(Mono.just(completedWorkout))
                .doOnSuccess(workout -> log.info("✅ Metrics calculated and events published for workout: {}", workout.getId()))
                .doOnError(error -> log.error("❌ Failed to calculate metrics or publish events for workout {}: {}",
                        completedWorkout.getId(), error.getMessage()));
    }

    /**
     * Calculate metrics using pure domain logic
     */
    private WorkoutMetrics calculateMetricsUsingDomain(WorkoutData workoutData) {
        log.debug("🧮 Calculating metrics using domain services for workout: {}", workoutData.getId());

        // Use pure domain calculation
        WorkoutMetrics metrics = metricsCalculator.calculateWorkoutMetrics(workoutData);

        log.debug("📈 Calculated metrics: volume={}, sets={}, reps={}, completedExercises={}",
                metrics.getTotalVolume().getValue(),
                metrics.getTotalSets(),
                metrics.getTotalReps(),
                metrics.getCompletedExercises());

        return metrics;
    }

    /**
     * Publish WorkoutCompletedEvent using domain calculations
     */
    private Mono<WorkoutCompletedEvent> publishWorkoutCompletedEvent(Workout workout, WorkoutMetrics metrics) {
        log.debug("📤 Publishing WorkoutCompletedEvent for workout: {}", workout.getId());

        // Convert to domain for additional calculations
        WorkoutData workoutData = domainMapper.toDomainWorkout(workout);

        List<String> muscleGroups = metricsCalculator.getWorkedMuscleGroups(workoutData);
        Integer caloriesBurned = metricsCalculator.estimateCaloriesBurned(workoutData);

        WorkoutCompletedEvent event = WorkoutCompletedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .userId(workout.getUserId())
                .workoutId(workout.getId())
                .workoutType(workout.getWorkoutType())
                .durationMinutes(workout.getDurationMinutes() != null ? workout.getDurationMinutes() : 0)
                .totalVolume(metrics.getTotalVolume().getValue())
                .totalSets(metrics.getTotalSets())
                .totalReps(metrics.getTotalReps())
                .exercisesCompleted(metrics.getCompletedExercises())
                .caloriesBurned(caloriesBurned)
                .workoutStartTime(workout.getStartedAt() != null ?
                        workout.getStartedAt().toInstant(ZoneOffset.UTC) :
                        workout.getWorkoutDate().toInstant(ZoneOffset.UTC))
                .workoutEndTime(workout.getCompletedAt() != null ?
                        workout.getCompletedAt().toInstant(ZoneOffset.UTC) :
                        Instant.now())
                .workedMuscleGroups(muscleGroups)
                .timestamp(Instant.now())
                .metadata(Map.of(
                        "calculationMethod", "domain-service",
                        "domainVersion", "1.0"
                ))
                .build();

        return eventPublisher.publishWorkoutCompleted(event)
                .then(Mono.just(event))
                .doOnSuccess(publishedEvent -> log.info("📤 Published WorkoutCompletedEvent: {}", publishedEvent.getEventId()));
    }

    /**
     * Check for personal records across all exercises
     */
    private Mono<Void> checkPersonalRecords(Workout workout) {
        log.debug("🏆 Checking personal records for workout: {}", workout.getId());

        if (workout.getExercises() == null || workout.getExercises().isEmpty()) {
            log.debug("⚠️ No exercises in workout, skipping PR check");
            return Mono.empty();
        }

        return reactor.core.publisher.Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> {
                    if (exercise.getExerciseId() == null || exercise.getExerciseName() == null) {
                        log.warn("⚠️ Exercise missing ID or name, skipping PR check");
                        return Mono.empty();
                    }

                    return personalRecordService.checkAndUpdatePersonalRecordsWithRetry(
                                    workout.getUserId(),
                                    exercise.getExerciseId(),
                                    exercise.getExerciseName(),
                                    exercise.getSets()
                            )
                            .onErrorResume(error -> {
                                log.warn("⚠️ PR check failed for exercise '{}' in workout {}: {} - continuing",
                                        exercise.getExerciseName(), workout.getId(), error.getMessage());
                                return Mono.just(new java.util.ArrayList<>());
                            });
                })
                .then()
                .doOnSuccess(v -> log.debug("🏆 Completed personal record checks for workout: {}", workout.getId()))
                .doOnError(error -> log.warn("⚠️ Some personal record checks failed for workout {}: {}",
                        workout.getId(), error.getMessage()));
    }

    /**
     * Apply additional completion data to the workout
     */
    private void applyCompletionData(Workout workout, Map<String, Object> completionData) {
        if (completionData == null) {
            return;
        }

        log.debug("📝 Applying completion data to workout: {}", workout.getId());

        if (completionData.containsKey("rating")) {
            workout.setRating((Integer) completionData.get("rating"));
        }

        if (completionData.containsKey("notes")) {
            String existingNotes = workout.getNotes();
            String newNotes = (String) completionData.get("notes");
            if (existingNotes != null && !existingNotes.isEmpty()) {
                workout.setNotes(existingNotes + "\n\nCompletion Notes: " + newNotes);
            } else {
                workout.setNotes(newNotes);
            }
        }

        if (completionData.containsKey("caloriesBurned")) {
            workout.setCaloriesBurned((Integer) completionData.get("caloriesBurned"));
        }

        if (completionData.containsKey("additionalTags")) {
            @SuppressWarnings("unchecked")
            List<String> additionalTags = (List<String>) completionData.get("additionalTags");
            if (additionalTags != null) {
                if (workout.getTags() == null) {
                    workout.setTags(new java.util.ArrayList<>());
                }
                for (String tag : additionalTags) {
                    if (!workout.getTags().contains(tag)) {
                        workout.getTags().add(tag);
                    }
                }
            }
        }
    }

    /**
     * Validate that a workout can be started (for future use)
     */
    public Mono<Workout> validateForStart(Workout workout) {
        var validationResult = validationService.validateForStart(workout);
        if (validationResult.isInvalid()) {
            return Mono.error(new InvalidWorkoutStateException(validationResult.getErrorMessage()));
        }
        return Mono.just(workout);
    }

    /**
     * Get workout efficiency metrics using domain calculations
     */
    public Mono<WorkoutCalculationService.WorkoutEfficiencyMetrics> getEfficiencyMetrics(String workoutId, Long userId) {
        return repository.findByIdAndUserId(workoutId, userId)
                .switchIfEmpty(Mono.error(new WorkoutNotFoundException("Workout not found: " + workoutId)))
                .map(workout -> {
                    WorkoutData workoutData = domainMapper.toDomainWorkout(workout);
                    Duration duration = metricsCalculator.calculateWorkoutDuration(workoutData);
                    int totalSets = metricsCalculator.calculateTotalSets(workoutData);
                    int totalReps = metricsCalculator.calculateTotalReps(workoutData);
                    var totalVolume = metricsCalculator.calculateTotalVolume(workoutData);

                    if (duration.toMinutes() == 0) {
                        return WorkoutCalculationService.WorkoutEfficiencyMetrics.empty();
                    }

                    double volumePerMinute = totalVolume.getValue().doubleValue() / duration.toMinutes();
                    double setsPerMinute = (double) totalSets / duration.toMinutes();
                    double repsPerMinute = (double) totalReps / duration.toMinutes();

                    return WorkoutCalculationService.WorkoutEfficiencyMetrics.builder()
                            .volumePerMinute(volumePerMinute)
                            .setsPerMinute(setsPerMinute)
                            .repsPerMinute(repsPerMinute)
                            .totalDuration(duration)
                            .build();
                });
    }
}
