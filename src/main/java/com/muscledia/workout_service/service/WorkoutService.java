package com.muscledia.workout_service.service;

import com.muscledia.workout_service.dto.request.StartWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.LogSetRequest;
import com.muscledia.workout_service.event.ExerciseCompletedEvent;
import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
import com.muscledia.workout_service.exception.ExerciseNotFoundException;
import com.muscledia.workout_service.exception.InvalidWorkoutStateException;
import com.muscledia.workout_service.exception.WorkoutNotFoundException;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import com.muscledia.workout_service.repository.WorkoutRepository;
import com.muscledia.workout_service.service.analytics.PersonalRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutService {
    private final WorkoutRepository workoutRepository;
    private final TransactionalEventPublisher transactionalEventPublisher;
    private final TransactionalOperator transactionalOperator; // Inject for reactive transactions
    private final PersonalRecordService personalRecordService;

    // WORKOUT SESSION MANAGEMENT

    /**
     * Start a new workout session
     */
    public Mono<Workout> startWorkout(Long userId, StartWorkoutRequest request) {
        log.info("Starting new workout session for user {}: {}", userId, request.getWorkoutName());

        Workout workout = Workout.builder()
                .userId(userId)
                .workoutName(request.getWorkoutName())
                .workoutPlanId(request.getWorkoutPlanId())
                .workoutType(request.getWorkoutType())
                .location(request.getLocation())
                .notes(request.getNotes())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .exercises(new ArrayList<>()) // Initialize empty exercises list
                .status(WorkoutStatus.IN_PROGRESS)
                .workoutDate(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .totalVolume(BigDecimal.ZERO)
                .totalSets(0)
                .totalReps(0)
                .build();

        return workoutRepository.save(workout)
                .doOnSuccess(saved -> log.info("Started workout session: {}", saved.getId()));
    }

    /**
     * Find workout by ID and user ID (security check)
     */
    public Mono<Workout> findByIdAndUserId(String workoutId, Long userId) {
        return workoutRepository.findByIdAndUserId(workoutId, userId)
                .switchIfEmpty(Mono.error(new WorkoutNotFoundException(
                        "Workout not found with ID: " + workoutId)));
    }


    /**
     * Get user's workouts with optional filtering
     */
    public Flux<Workout> getUserWorkouts(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate != null || endDate != null) {
            LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDateTime.now().minusYears(1);
            LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDateTime.now();
            return workoutRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, start, end);
        }
        return workoutRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    // SET LOGGING - Core functionality for the user story

    /**
     * Log a new set for a specific exercise - This implements the user story!
     */
    public Mono<Workout> logSet(String workoutId, Long userId, int exerciseIndex, LogSetRequest setRequest) {
        log.info("Logging set for workout {} exercise {}: {}kg x {} reps",
                workoutId, exerciseIndex, setRequest.getWeightKg(), setRequest.getReps());

        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate workout state
                            if (!workout.isActive()) {
                                return Mono.error(new InvalidWorkoutStateException(
                                        "Cannot log sets for a non-active workout. Current status: " + workout.getStatus()));
                            }

                            // Validate exercise index
                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException(
                                        String.format("Exercise index %d not found. Workout has %d exercises.",
                                                exerciseIndex, workout.getExercises().size())));
                            }

                            // Create the new set with precise performance data
                            WorkoutSet newSet = createWorkoutSetFromRequest(setRequest);

                            // Add set to the exercise
                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            exercise.addSet(newSet);

                            // Recalculate workout metrics
                            workout.recalculateMetrics();

                            return workoutRepository.save(workout);
                        })
                        .publishOn(Schedulers.boundedElastic())
                        .doOnSuccess(savedWorkout -> {
                            log.info("Successfully logged set for workout {}: {}kg x {} reps",
                                    workoutId, setRequest.getWeightKg(), setRequest.getReps());

                            // Check for personal records asynchronously
                            checkForPersonalRecords(savedWorkout, exerciseIndex)
                                    .subscribe(
                                            result -> log.info("PR check completed for workout {}", workoutId),
                                            error -> log.warn("PR check failed for workout {}: {}", workoutId, error.getMessage())
                                    );
                        })
        ).single();
    }

    /**
     * Update an existing set
     */
    public Mono<Workout> updateSet(String workoutId, Long userId, int exerciseIndex, int setIndex, LogSetRequest setRequest) {
        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate indices
                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException("Invalid exercise index: " + exerciseIndex));
                            }

                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            if (setIndex < 0 || setIndex >= exercise.getSets().size()) {
                                return Mono.error(new IllegalArgumentException("Invalid set index: " + setIndex));
                            }

                            // Update the set
                            WorkoutSet existingSet = exercise.getSets().get(setIndex);
                            updateWorkoutSetFromRequest(existingSet, setRequest);

                            // Recalculate metrics
                            workout.recalculateMetrics();

                            return workoutRepository.save(workout);
                        })
                        .doOnSuccess(saved -> log.info("Successfully updated set for workout {}", workoutId))
        ).single();
    }

    /**
     * Delete a set from an exercise
     */
    public Mono<Void> deleteSet(String workoutId, Long userId, int exerciseIndex, int setIndex) {
        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate indices
                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException("Invalid exercise index: " + exerciseIndex));
                            }

                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            if (setIndex < 0 || setIndex >= exercise.getSets().size()) {
                                return Mono.error(new IllegalArgumentException("Invalid set index: " + setIndex));
                            }

                            // Remove set and renumber
                            exercise.getSets().remove(setIndex);
                            renumberSets(exercise.getSets());

                            // Recalculate metrics
                            workout.recalculateMetrics();

                            return workoutRepository.save(workout);
                        })
                        .then()
        ).then();
    }

    /**
     * Add a new exercise to an active workout
     */
    public Mono<Workout> addExerciseToWorkout(String workoutId, Long userId, WorkoutExercise exercise) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    if (!workout.isActive()) {
                        return Mono.error(new InvalidWorkoutStateException("Cannot add exercises to inactive workout"));
                    }

                    // Initialize sets list if null
                    if (exercise.getSets() == null) {
                        exercise.setSets(new ArrayList<>());
                    }

                    workout.addExercise(exercise);
                    return workoutRepository.save(workout);
                })
                .doOnSuccess(saved -> log.info("Added exercise {} to workout {}",
                        exercise.getExerciseName(), workoutId));
    }

    // WORKOUT COMPLETION

    /**
     * Complete a workout session with comprehensive event publishing
     */
    public Mono<Workout> completeWorkout(String workoutId, Long userId, Map<String, Object> completionData) {
        log.info("Completing workout session: {}", workoutId);

        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            if (workout.isCompleted()) {
                                return Mono.error(new InvalidWorkoutStateException("Workout is already completed"));
                            }

                            // Apply completion data
                            if (completionData != null) {
                                applyCompletionData(workout, completionData);
                            }

                            // Complete the workout (calculates metrics)
                            workout.completeWorkout();

                            return workoutRepository.save(workout);
                        })
                        .flatMap(completedWorkout -> {
                            // Publish workout completed event for gamification
                            return publishWorkoutCompletedEvent(completedWorkout)
                                    .then(publishExerciseCompletedEvents(completedWorkout))
                                    .then(checkForPersonalRecordsAllExercises(completedWorkout))
                                    .then(Mono.just(completedWorkout));
                        })
                        .doOnSuccess(workout -> log.info("Successfully completed workout: {}", workoutId))
        ).single();
    }

    // ANALYTICS AND PERSONAL RECORDS

    /**
     * Check for personal records in a specific exercise
     */
    private Mono<Void> checkForPersonalRecords(Workout workout, int exerciseIndex) {
        if (exerciseIndex >= workout.getExercises().size()) {
            return Mono.empty();
        }

        WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
        return personalRecordService.checkAndUpdatePersonalRecords(
                workout.getUserId(),
                exercise.getExerciseId(),
                exercise.getSets()
        ).then();
    }

    /**
     * Check for personal records across all exercises in the workout
     */
    private Mono<Void> checkForPersonalRecordsAllExercises(Workout workout) {
        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> personalRecordService.checkAndUpdatePersonalRecords(
                        workout.getUserId(),
                        exercise.getExerciseId(),
                        exercise.getSets()
                ))
                .then();
    }

    // EVENT PUBLISHING

    /**
     * Publish workout completed event for gamification service
     */
    private Mono<Void> publishWorkoutCompletedEvent(Workout workout) {
        WorkoutCompletedEvent event = WorkoutCompletedEvent.builder()
                .userId(workout.getUserId())
                .workoutId(workout.getId())
                .workoutType(workout.getWorkoutType())
                .durationMinutes(workout.getDurationMinutes())
                .totalVolume(workout.getTotalVolume())
                .totalSets(workout.getTotalSets())
                .totalReps(workout.getTotalReps())
                .exercisesCompleted(workout.getExercises().size())
                .caloriesBurned(workout.getCaloriesBurned()) // Add this if available
                // Convert LocalDateTime to Instant
                .workoutStartTime(workout.getStartedAt().toInstant(ZoneOffset.UTC))
                .workoutEndTime(workout.getCompletedAt().toInstant(ZoneOffset.UTC))
                .workedMuscleGroups(workout.getWorkedMuscleGroups())
                .build();

        return transactionalEventPublisher.publishWorkoutCompleted(event)
                .doOnSuccess(v -> log.info("Published WorkoutCompletedEvent for workout: {}", workout.getId()));
    }

    /**
     * Publish individual exercise completed events
     */
    private Mono<Void> publishExerciseCompletedEvents(Workout workout) {
        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> {
                    // Calculate exercise metrics properly
                    int setsCompleted = exercise.getSets() != null ? exercise.getSets().size() : 0;
                    int totalReps = exercise.getTotalReps();
                    BigDecimal totalVolume = exercise.getTotalVolume();
                    BigDecimal maxWeight = exercise.getMaxWeight();

                    // Get duration for duration-based exercises (like warm-up)
                    Integer totalDuration = null;
                    if (exercise.getSets() != null) {
                        totalDuration = exercise.getSets().stream()
                                .filter(set -> set.getDurationSeconds() != null)
                                .mapToInt(WorkoutSet::getDurationSeconds)
                                .sum();
                    }

                    ExerciseCompletedEvent event = ExerciseCompletedEvent.builder()
                            .userId(workout.getUserId())
                            .workoutId(workout.getId())
                            .exerciseId(exercise.getExerciseId())
                            .exerciseName(exercise.getExerciseName())
                            .exerciseCategory(exercise.getExerciseCategory())
                            .setsCompleted(setsCompleted)
                            .totalReps(totalReps)
                            .totalVolume(totalVolume)
                            .maxWeight(maxWeight)
                            .primaryMuscleGroup(exercise.getPrimaryMuscleGroup())
                            // Add duration for warmup/cardio exercises
                            .durationSeconds(totalDuration != null && totalDuration > 0 ? totalDuration : null)
                            .equipment(exercise.getEquipment())
                            .build();

                    // Log validation details for debugging
                    log.debug("Publishing ExerciseCompletedEvent: exerciseId={}, category={}, sets={}, reps={}, duration={}, valid={}",
                            event.getExerciseId(), event.getExerciseCategory(), event.getSetsCompleted(),
                            event.getTotalReps(), event.getDurationSeconds(), event.isValid());

                    return transactionalEventPublisher.publishExerciseCompleted(event);
                })
                .then()
                .doOnSuccess(v -> log.info("Published ExerciseCompletedEvents for workout: {}", workout.getId()));
    }

    // HELPER METHODS

    /**
     * Create WorkoutSet from LogSetRequest
     */
    private WorkoutSet createWorkoutSetFromRequest(LogSetRequest request) {
        WorkoutSet set = WorkoutSet.builder()
                .weightKg(request.getWeightKg())
                .reps(request.getReps())
                .durationSeconds(request.getDurationSeconds())
                .distanceMeters(request.getDistanceMeters())
                .restSeconds(request.getRestSeconds())
                .rpe(request.getRpe())
                .completed(request.getCompleted())
                .failure(request.getFailure())
                .dropSet(request.getDropSet())
                .warmUp(request.getWarmUp())
                .setType(request.getSetType())
                .notes(request.getNotes())
                .startedAt(LocalDateTime.now())
                .build();

        if (Boolean.TRUE.equals(request.getCompleted())) {
            set.markCompleted();
        }

        return set;
    }

    /**
     * Update existing WorkoutSet from LogSetRequest
     */
    private void updateWorkoutSetFromRequest(WorkoutSet existingSet, LogSetRequest request) {
        existingSet.setWeightKg(request.getWeightKg());
        existingSet.setReps(request.getReps());
        existingSet.setDurationSeconds(request.getDurationSeconds());
        existingSet.setDistanceMeters(request.getDistanceMeters());
        existingSet.setRestSeconds(request.getRestSeconds());
        existingSet.setRpe(request.getRpe());
        existingSet.setCompleted(request.getCompleted());
        existingSet.setFailure(request.getFailure());
        existingSet.setDropSet(request.getDropSet());
        existingSet.setWarmUp(request.getWarmUp());
        existingSet.setSetType(request.getSetType());
        existingSet.setNotes(request.getNotes());

        if (Boolean.TRUE.equals(request.getCompleted()) && existingSet.getCompletedAt() == null) {
            existingSet.markCompleted();
        }
    }

    /**
     * Renumber sets after deletion
     */
    private void renumberSets(List<WorkoutSet> sets) {
        for (int i = 0; i < sets.size(); i++) {
            sets.get(i).setSetNumber(i + 1);
        }
    }

    /**
     * Apply completion data to workout
     */
    private void applyCompletionData(Workout workout, Map<String, Object> completionData) {
        if (completionData.containsKey("rating")) {
            workout.setRating((Integer) completionData.get("rating"));
        }
        if (completionData.containsKey("notes")) {
            workout.setNotes((String) completionData.get("notes"));
        }
        if (completionData.containsKey("caloriesBurned")) {
            workout.setCaloriesBurned((Integer) completionData.get("caloriesBurned"));
        }
    }

    // ADDITIONAL QUERY METHODS (keeping your existing ones)

    public Flux<Workout> findByUserAndVolumeRange(Long userId, BigDecimal minVolume, BigDecimal maxVolume) {
        return workoutRepository.findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(userId, minVolume, maxVolume);
    }

    public Flux<Workout> findByUserAndDurationRange(Long userId, Integer minDuration, Integer maxDuration) {
        return workoutRepository.findByUserIdAndDurationMinutesBetweenOrderByWorkoutDateDesc(userId, minDuration, maxDuration);
    }

    public Flux<Workout> findRecentWorkouts(Long userId) {
        return workoutRepository.findTop10ByUserIdOrderByWorkoutDateDesc(userId);
    }

    public Mono<Long> countWorkoutsInDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return workoutRepository.countByUserIdAndWorkoutDateBetween(userId, startDate, endDate);
    }

    public Flux<Workout> findByUserAndExercise(Long userId, String exerciseId) {
        return workoutRepository.findByUserIdAndExerciseId(userId, exerciseId);
    }

    /**
     * ADDED: Save method for general use
     */
    public Mono<Workout> save(Workout workout) {
        return workoutRepository.save(workout)
                .doOnSuccess(saved -> log.debug("Saved workout: {}", saved.getId()));
    }

    /**
     * ADDED: Delete workout by ID
     */
    public Mono<Void> deleteById(String id) {
        return workoutRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted workout with id: {}", id));
    }

    /**
     * ADDED: Find all workouts (admin use)
     */
    public Flux<Workout> findAll() {
        return workoutRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all workouts"));
    }

    /**
     * ADDED: Cancel workout
     */
    public Mono<Workout> cancelWorkout(String workoutId, Long userId) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    workout.cancelWorkout();
                    return save(workout);
                })
                .doOnSuccess(workout -> log.info("Cancelled workout: {}", workoutId));
    }

}