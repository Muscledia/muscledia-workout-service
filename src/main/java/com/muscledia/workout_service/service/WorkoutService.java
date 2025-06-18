package com.muscledia.workout_service.service;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutService {
    private final WorkoutRepository workoutRepository;

    public Mono<Workout> findById(String id) {
        return Mono.fromCallable(() -> workoutRepository.findById(id).orElse(null))
                .doOnNext(workout -> {
                    if (workout != null) {
                        log.debug("Found workout for date: {}", workout.getWorkoutDate());
                    }
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Workout not found with id: " + id)));
    }

    public Flux<Workout> findByUser(Long userId) {
        return Flux.fromIterable(workoutRepository.findByUserIdOrderByWorkoutDateDesc(userId))
                .doOnComplete(() -> log.debug("Retrieved workouts for user: {}", userId));
    }

    public Flux<Workout> findByUserAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return Flux.fromIterable(
                workoutRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, startDate, endDate))
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} between {} and {}",
                        userId, startDate, endDate));
    }

    public Flux<Workout> findByUserAndVolumeRange(Long userId, BigDecimal minVolume, BigDecimal maxVolume) {
        return Flux.fromIterable(
                workoutRepository.findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(userId, minVolume, maxVolume))
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} with volume between {} and {}",
                        userId, minVolume, maxVolume));
    }

    public Flux<Workout> findByUserAndDurationRange(Long userId, Integer minDuration, Integer maxDuration) {
        return Flux.fromIterable(
                workoutRepository.findByUserIdAndDurationMinutesBetweenOrderByWorkoutDateDesc(
                        userId, minDuration, maxDuration))
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} with duration between {} and {} minutes",
                        userId, minDuration, maxDuration));
    }

    public Flux<Workout> findRecentWorkouts(Long userId) {
        return Flux.fromIterable(workoutRepository.findTop10ByUserIdOrderByWorkoutDateDesc(userId))
                .doOnComplete(() -> log.debug("Retrieved recent workouts for user: {}", userId));
    }

    public Mono<Long> countWorkoutsInDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return Mono.fromCallable(() -> workoutRepository.countByUserIdAndWorkoutDateBetween(userId, startDate, endDate))
                .doOnSuccess(count -> log.debug("Counted {} workouts for user {} between {} and {}",
                        count, userId, startDate, endDate));
    }

    public Flux<Workout> findByUserAndExercise(Long userId, String exerciseId) {
        return Flux.fromIterable(workoutRepository.findByUserIdAndExerciseId(userId, exerciseId))
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} containing exercise {}",
                        userId, exerciseId));
    }

    public Flux<Workout> findByUserAndMinVolume(Long userId, BigDecimal minVolume) {
        return Flux.fromIterable(
                workoutRepository.findByUserIdAndTotalVolumeGreaterThanEqual(userId, minVolume))
                .doOnComplete(() -> log.debug("Retrieved workouts for user {} with minimum volume {}",
                        userId, minVolume));
    }

    public Mono<Workout> save(Workout workout) {
        return Mono.fromCallable(() -> workoutRepository.save(workout))
                .doOnSuccess(saved -> log.debug("Saved workout for date: {}", saved.getWorkoutDate()));
    }

    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> workoutRepository.deleteById(id))
                .then()
                .doOnSuccess(v -> log.debug("Deleted workout with id: {}", id));
    }

    public Flux<Workout> findAll() {
        return Flux.fromIterable(workoutRepository.findAll())
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
                            .multiply(exercise.getWeight());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}