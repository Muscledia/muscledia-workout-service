package com.muscledia.workout_service.domain.service;

import com.muscledia.workout_service.domain.vo.WorkoutMetrics;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Domain Service for workout calculations
 * Handles all metric computation logic extracted from Workout model
 * Uses stored data from workout exercises instead of external service lookups
 */
@Service
@Slf4j
public class WorkoutCalculationService {

    /**
     * Calculate workout intensity score (0-10 scale)
     */
    public Double calculateIntensityScore(Workout workout) {
        if (workout == null) {
            return 0.0;
        }

        WorkoutMetrics metrics = calculateWorkoutMetrics(workout);
        Duration duration = calculateWorkoutDuration(workout);

        if (metrics.isEmpty() || duration.toMinutes() == 0) {
            return 0.0;
        }

        // Volume per minute
        double volumePerMinute = metrics.getTotalVolume().getValue().doubleValue() / duration.toMinutes();

        // Sets per minute (density)
        double setsPerMinute = (double) metrics.getTotalSets() / duration.toMinutes();

        // Completion rate
        double completionRate = workout.getExercises() == null || workout.getExercises().isEmpty() ? 0.0 :
                (double) metrics.getCompletedExercises() / workout.getExercises().size();

        // Weighted score (0-10 scale)
        double intensityScore = Math.min(10.0,
                (volumePerMinute * 0.01) +
                        (setsPerMinute * 2.0) +
                        (completionRate * 3.0)
        );

        return Math.round(intensityScore * 10.0) / 10.0;
    }

    /**
     * Get workout efficiency metrics
     */
    public WorkoutEfficiencyMetrics calculateEfficiencyMetrics(Workout workout) {
        if (workout == null) {
            return WorkoutEfficiencyMetrics.empty();
        }

        WorkoutMetrics metrics = calculateWorkoutMetrics(workout);
        Duration duration = calculateWorkoutDuration(workout);

        if (duration.toMinutes() == 0) {
            return WorkoutEfficiencyMetrics.empty();
        }

        double volumePerMinute = metrics.getTotalVolume().getValue().doubleValue() / duration.toMinutes();
        double setsPerMinute = (double) metrics.getTotalSets() / duration.toMinutes();
        double repsPerMinute = (double) metrics.getTotalReps() / duration.toMinutes();

        return WorkoutEfficiencyMetrics.builder()
                .volumePerMinute(volumePerMinute)
                .setsPerMinute(setsPerMinute)
                .repsPerMinute(repsPerMinute)
                .totalDuration(duration)
                .build();
    }

    /**
     * Calculate workout completion percentage
     */
    public Double calculateCompletionPercentage(Workout workout) {
        if (workout == null || workout.getExercises() == null || workout.getExercises().isEmpty()) {
            return 0.0;
        }

        long totalExercises = workout.getExercises().size();
        long completedExercises = workout.getExercises().stream()
                .mapToLong(exercise -> exercise.isFullyCompleted() ? 1L : 0L)
                .sum();

        return (double) completedExercises / totalExercises * 100.0;
    }

    /**
     * Get summary of exercise types in the workout
     */
    public List<String> getExerciseCategories(Workout workout) {
        if (workout == null || workout.getExercises() == null) {
            return List.of();
        }

        return workout.getExercises().stream()
                .map(WorkoutExercise::getExerciseCategory)
                .filter(category -> category != null && !category.trim().isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Calculate average rest time between sets
     */
    public Double calculateAverageRestTime(Workout workout) {
        if (workout == null || workout.getExercises() == null) {
            return 0.0;
        }

        List<Integer> restTimes = new ArrayList<>();

        workout.getExercises().forEach(exercise -> {
            if (exercise.getSets() != null) {
                exercise.getSets().stream()
                        .map(WorkoutSet::getRestSeconds)
                        .filter(restSeconds -> restSeconds != null && restSeconds > 0)
                        .forEach(restTimes::add);
            }
        });

        return restTimes.isEmpty() ? 0.0 :
                restTimes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    public WorkoutMetrics calculateWorkoutMetrics(Workout workout) {
        if (workout == null || workout.getExercises() == null) return WorkoutMetrics.empty();
        return WorkoutMetrics.calculate(workout.getExercises());
    }

    public Duration calculateWorkoutDuration(Workout workout) {
        if (workout == null) return Duration.ZERO;
        if (workout.getStartedAt() != null && workout.getCompletedAt() != null) {
            return Duration.between(workout.getStartedAt(), workout.getCompletedAt());
        }
        if (workout.getDurationMinutes() != null) {
            return Duration.ofMinutes(workout.getDurationMinutes());
        }
        return Duration.ZERO;
    }

    public List<String> getWorkedMuscleGroups(Workout workout) {
        if (workout == null || workout.getExercises() == null) return List.of();
        List<String> muscleGroups = new ArrayList<>();
        workout.getExercises().forEach(exercise -> {
            if (exercise.getPrimaryMuscleGroup() != null) muscleGroups.add(exercise.getPrimaryMuscleGroup().toLowerCase());
            if (exercise.getSecondaryMuscleGroups() != null) {
                exercise.getSecondaryMuscleGroups().forEach(mg -> muscleGroups.add(mg.toLowerCase()));
            }
        });
        return muscleGroups.stream().distinct().sorted().collect(Collectors.toList());
    }

    public Integer estimateCaloriesBurned(Workout workout) {
        if (workout == null) return 0;
        WorkoutMetrics metrics = calculateWorkoutMetrics(workout);
        Duration duration = calculateWorkoutDuration(workout);
        if (duration.toMinutes() == 0) return 0;

        double strengthTrainingMETs = 6.0;
        double bodyWeightKg = 70.0;
        double baseCalories = (strengthTrainingMETs * bodyWeightKg * duration.toMinutes()) / 60.0;
        double volumeBonus = metrics.getTotalVolume().getValue().doubleValue() * 0.0005;
        double completionBonus = metrics.getTotalSets() * 1.5;

        return Math.max(0, Math.min((int) Math.round(baseCalories + volumeBonus + completionBonus), 2000));
    }

    // ============================================================================================
    //  MISSING GRANULAR METHODS (Added these to fix your Errors)
    // ============================================================================================

    public BigDecimal calculateSetVolume(WorkoutSet set) {
        if (set == null || set.getWeightKg() == null || set.getReps() == null) {
            return BigDecimal.ZERO;
        }
        return set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps()));
    }

    public BigDecimal calculateExerciseTotalVolume(WorkoutExercise exercise) {
        if (exercise == null || exercise.getSets() == null) return BigDecimal.ZERO;
        return exercise.getSets().stream()
                .map(this::calculateSetVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Integer calculateExerciseTotalReps(WorkoutExercise exercise) {
        if (exercise == null || exercise.getSets() == null) return 0;
        return exercise.getSets().stream()
                .filter(set -> set.getReps() != null)
                .mapToInt(WorkoutSet::getReps)
                .sum();
    }

    public BigDecimal calculateExerciseMaxWeight(WorkoutExercise exercise) {
        if (exercise == null || exercise.getSets() == null) return BigDecimal.ZERO;
        return exercise.getSets().stream()
                .map(WorkoutSet::getWeightKg)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    public Double calculateExerciseAverageRpe(WorkoutExercise exercise) {
        if (exercise == null || exercise.getSets() == null) return null;
        return exercise.getSets().stream()
                .filter(set -> set.getRpe() != null)
                .mapToInt(WorkoutSet::getRpe)
                .average()
                .orElse(0.0);
    }

    public Long calculateCompletedSetsCount(WorkoutExercise exercise) {
        if (exercise == null || exercise.getSets() == null) return 0L;
        return exercise.getSets().stream()
                .filter(set -> Boolean.TRUE.equals(set.getCompleted()))
                .count();
    }

    /**
     * Value object for efficiency metrics
     */
    @Builder
    @Data
    public static class WorkoutEfficiencyMetrics {
        private final double volumePerMinute;
        private final double setsPerMinute;
        private final double repsPerMinute;
        private final Duration totalDuration;

        public static WorkoutEfficiencyMetrics empty() {
            return WorkoutEfficiencyMetrics.builder()
                    .volumePerMinute(0.0)
                    .setsPerMinute(0.0)
                    .repsPerMinute(0.0)
                    .totalDuration(Duration.ZERO)
                    .build();
        }
    }
}
