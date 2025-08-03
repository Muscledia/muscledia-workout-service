package com.muscledia.workout_service.service;

import com.muscledia.workout_service.event.ExerciseCompletedEvent;
import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutService {
    private final WorkoutRepository workoutRepository;
    private final TransactionalEventPublisher transactionalEventPublisher;
    private final TransactionalOperator transactionalOperator; // Inject for reactive transactions

    public Mono<Workout> findById(String id) {
        return workoutRepository.findById(id) // Reactive findById
                .doOnNext(workout -> {
                    if (workout != null) {
                        log.debug("Found workout for date: {}", workout.getWorkoutDate());
                    }
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Workout not found with id: " + id)));
    }

    public Flux<Workout> findByUser(Long userId) {
        return workoutRepository.findByUserIdOrderByWorkoutDateDesc(userId) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved workouts for user: {}", userId));
    }

    public Flux<Workout> findByUserAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return workoutRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, startDate, endDate) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} between {} and {}",
                        userId, startDate, endDate));
    }

    public Flux<Workout> findByUserAndVolumeRange(Long userId, BigDecimal minVolume, BigDecimal maxVolume) {
        return workoutRepository.findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(userId, minVolume, maxVolume) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} with volume between {} and {}",
                        userId, minVolume, maxVolume));
    }

    public Flux<Workout> findByUserAndDurationRange(Long userId, Integer minDuration, Integer maxDuration) {
        return workoutRepository.findByUserIdAndDurationMinutesBetweenOrderByWorkoutDateDesc(
                        userId, minDuration, maxDuration) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} with duration between {} and {} minutes",
                        userId, minDuration, maxDuration));
    }

    public Flux<Workout> findRecentWorkouts(Long userId) {
        return workoutRepository.findTop10ByUserIdOrderByWorkoutDateDesc(userId) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved recent workouts for user: {}", userId));
    }

    public Mono<Long> countWorkoutsInDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return workoutRepository.countByUserIdAndWorkoutDateBetween(userId, startDate, endDate) // Reactive call
                .doOnSuccess(count -> log.debug("Counted {} workouts for user {} between {} and {}",
                        count, userId, startDate, endDate));
    }

    public Flux<Workout> findByUserAndExercise(Long userId, String exerciseId) {
        return workoutRepository.findByUserIdAndExerciseId(userId, exerciseId) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} containing exercise {}",
                        userId, exerciseId));
    }

    public Flux<Workout> findByUserAndMinVolume(Long userId, BigDecimal minVolume) {
        return workoutRepository.findByUserIdAndTotalVolumeGreaterThanEqual(userId, minVolume) // Reactive call
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} with minimum volume {}",
                        userId, minVolume));
    }

    public Mono<Workout> save(Workout workout) {
        return workoutRepository.save(workout) // Reactive save
                .doOnSuccess(saved -> log.debug("Saved workout for date: {}", saved.getWorkoutDate()));
    }

    public Mono<Void> deleteById(String id) {
        return workoutRepository.deleteById(id) // Reactive delete
                .doOnSuccess(v -> log.debug("Deleted workout with id: {}", id));
    }

    public Flux<Workout> findAll() {
        return workoutRepository.findAll() // Reactive findAll
                .doOnComplete(() -> log.debug("Retrieved all workouts"));
    }

    public Mono<Workout> saveWorkoutAndCalculateVolume(Workout workout) {
        // Calculate total volume before saving
        BigDecimal totalVolume = calculateTotalVolume(workout);
        workout.setTotalVolume(totalVolume);

        return save(workout)
                .doOnSuccess(saved -> log.debug("Saved workout with calculated volume: {}", saved.getTotalVolume()));
    }

    private BigDecimal calculateTotalVolume(Workout workout) {
        if (workout.getExercises() == null) {
            return BigDecimal.ZERO;
        }

        return workout.getExercises().stream()
                .map(exercise -> {
                    if (exercise.getReps() == null || exercise.getWeight() == null) {
                        return BigDecimal.ZERO;
                    }
                    return BigDecimal.valueOf(exercise.getReps())
                            .multiply(BigDecimal.valueOf(exercise.getWeight()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Core business method to complete a workout, save it, and publish events.
     * This method is wrapped in a reactive transaction to ensure atomicity with the outbox.
     */
    public Flux<String> completeWorkout(Long userId, String workoutId, String workoutType,
                                        Integer durationMinutes, Integer caloriesBurned,
                                        Integer exercisesCompletedCount, Integer totalSetsCount, Integer totalRepsCount,
                                        Instant workoutStartTime, Instant workoutEndTime, // Using Instant for events
                                        List<Map<String, Object>> exerciseLogDetails,
                                        Map<String, Object> workoutMetadata) {

        // The entire sequence of saving workout and publishing events must be transactional
        return transactionalOperator.execute(status -> {
                    // 1. Convert raw exerciseLogDetails to List<WorkoutExercise> for the Workout model
                    List<WorkoutExercise> workoutExercises = exerciseLogDetails.stream()
                            .map(log -> WorkoutExercise.builder()
                                            .exerciseId((String) log.get("exerciseId")) // Ensure exerciseId is in your log map
                                            .exerciseName((String) log.get("exerciseName"))
                                            .exerciseCategory((String) log.get("exerciseCategory"))
                                            .sets((Integer) log.get("setsCompleted"))
                                            .reps(((Integer) log.get("totalReps")))
                                            .weight(log.containsKey("weight") ? ((Number) log.get("weight")).doubleValue() : null)
                                            .weightUnit((String) log.get("weightUnit"))
                                            .durationSeconds((Integer) log.get("durationSeconds"))
                                            .distance(log.containsKey("distance") ? ((Number) log.get("distance")).doubleValue() : null)
                                            .distanceUnit((String) log.get("distanceUnit"))
                                            .order((Integer) log.get("order")) // Add 'order' field
                                            .notes((String) log.get("notes")) // Add 'notes' field
                                            .build()
                            )
                            .collect(Collectors.toList());

                    // 2. Create and save workout to the database reactively
                    Workout workout = Workout.builder()
                            .id(workoutId)
                            .userId(userId)
                            .workoutType(workoutType) // Now present in model
                            .durationMinutes(durationMinutes)
                            .caloriesBurned(caloriesBurned) // Now present in model
                            .exercisesCompleted(exercisesCompletedCount) // Now present in model
                            .totalSets(totalSetsCount) // Now present in model
                            .totalReps(totalRepsCount) // Now present in model
                            .workoutDate(LocalDateTime.ofInstant(workoutEndTime, ZoneOffset.UTC)) // Correct Instant to LocalDateTime conversion
                            .startTime(workoutStartTime) // Now present in model
                            .endTime(workoutEndTime) // Now present in model
                            .metadata(workoutMetadata) // Now present in model
                            .status("COMPLETED") // Now present in model
                            .exercises(workoutExercises) // Use the converted list of WorkoutExercise
                            .build();

                    // Calculate total volume before saving using the WorkoutExercise list
                    BigDecimal totalVolume = calculateTotalVolume(workout);
                    workout.setTotalVolume(totalVolume);


                    return workoutRepository.save(workout)
                            .doOnSuccess(savedWorkout -> log.info("Workout saved: {} for user: {}", savedWorkout.getId(), userId))
                            .flatMap(savedWorkout -> {
                                // 2. Publish WorkoutCompletedEvent
                                WorkoutCompletedEvent workoutEvent = WorkoutCompletedEvent.builder()
                                        .userId(userId)
                                        .workoutId(savedWorkout.getId())
                                        .workoutType(workoutType)
                                        .durationMinutes(durationMinutes)
                                        .caloriesBurned(caloriesBurned)
                                        .exercisesCompleted(exercisesCompletedCount)
                                        .totalSets(totalSetsCount)
                                        .totalReps(totalRepsCount)
                                        .workoutStartTime(workoutStartTime)
                                        .workoutEndTime(workoutEndTime)
                                        .metadata(workoutMetadata)
                                        .build();
                                return transactionalEventPublisher.publishWorkoutCompleted(workoutEvent)
                                        .doOnSuccess(v -> log.info("WorkoutCompletedEvent published for workout: {}", savedWorkout.getId()))
                                        .then(Mono.just(savedWorkout)); // Continue with the saved workout

                            })
                            .flatMapMany(savedWorkout -> {
                                // 3. Publish individual ExerciseCompletedEvents (if any)
                                if (!exerciseLogDetails.isEmpty()) {
                                    return Flux.fromIterable(exerciseLogDetails)
                                            .flatMap(exerciseLog -> {
                                                ExerciseCompletedEvent exerciseEvent = ExerciseCompletedEvent.builder()
                                                        .userId(userId)
                                                        .exerciseName((String) exerciseLog.get("exerciseName"))
                                                        .exerciseCategory((String) exerciseLog.get("exerciseCategory"))
                                                        .workoutId(savedWorkout.getId())
                                                        .setsCompleted((Integer) exerciseLog.get("setsCompleted"))
                                                        .totalReps((Integer) exerciseLog.get("totalReps"))
                                                        .weight(exerciseLog.containsKey("weight") ? ((Number) exerciseLog.get("weight")).doubleValue() : null)
                                                        .weightUnit((String) exerciseLog.get("weightUnit"))
                                                        .durationSeconds((Integer) exerciseLog.get("durationSeconds"))
                                                        .distance(exerciseLog.containsKey("distance") ? ((Number) exerciseLog.get("distance")).doubleValue() : null)
                                                        .distanceUnit((String) exerciseLog.get("distanceUnit"))
                                                        .setDetails((List<Map<String, Object>>) exerciseLog.get("setDetails"))
                                                        .equipment((String) exerciseLog.get("equipment"))
                                                        .metadata((Map<String, Object>) exerciseLog.get("metadata"))
                                                        .build();
                                                return transactionalEventPublisher.publishExerciseCompleted(exerciseEvent);
                                            });
                                }
                                return Flux.empty(); // No exercise events
                            })
                            .then(Mono.just("Workout completed and events published successfully: " + workoutId)); // Final result
                })
                .onErrorResume(e -> {
                    log.error("Failed to complete workout or publish events for user {}: {}", userId, e.getMessage(), e);
                    return Mono.error(new RuntimeException("Workout completion failed: " + e.getMessage(), e));
                });
    }

}