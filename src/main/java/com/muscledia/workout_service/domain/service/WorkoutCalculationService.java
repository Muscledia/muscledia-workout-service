package com.muscledia.workout_service.domain.service;

import com.muscledia.workout_service.domain.vo.WorkoutMetrics;
import com.muscledia.workout_service.model.Workout;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
     * Calculate comprehensive workout metrics
     */
    public WorkoutMetrics calculateWorkoutMetrics(Workout workout) {
        if (workout == null || workout.getExercises() == null) {
            return WorkoutMetrics.empty();
        }

        return WorkoutMetrics.calculate(workout.getExercises());
    }

    /**
     * Calculate total workout duration
     */
    public Duration calculateWorkoutDuration(Workout workout) {
        if (workout == null) {
            return Duration.ZERO;
        }

        if (workout.getStartedAt() != null && workout.getCompletedAt() != null) {
            return Duration.between(workout.getStartedAt(), workout.getCompletedAt());
        }

        // Fallback to stored duration
        if (workout.getDurationMinutes() != null) {
            return Duration.ofMinutes(workout.getDurationMinutes());
        }

        return Duration.ZERO;
    }

    /**
     * Get all unique muscle groups worked in this workout
     * Uses stored muscle group data from workout exercises (no external service calls)
     */
    public List<String> getWorkedMuscleGroups(Workout workout) {
        if (workout == null || workout.getExercises() == null || workout.getExercises().isEmpty()) {
            return List.of();
        }

        List<String> muscleGroups = new ArrayList<>();

        workout.getExercises().forEach(exercise -> {
            // Get muscle groups from stored data on exercise
            if (exercise.getPrimaryMuscleGroup() != null && !exercise.getPrimaryMuscleGroup().trim().isEmpty()) {
                muscleGroups.add(exercise.getPrimaryMuscleGroup().toLowerCase());
            }

            if (exercise.getSecondaryMuscleGroups() != null) {
                exercise.getSecondaryMuscleGroups().stream()
                        .filter(muscleGroup -> muscleGroup != null && !muscleGroup.trim().isEmpty())
                        .map(String::toLowerCase)
                        .forEach(muscleGroups::add);
            }
        });

        return muscleGroups.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Estimate calories burned using improved calculation
     */
    public Integer estimateCaloriesBurned(Workout workout) {
        if (workout == null) {
            return 0;
        }

        WorkoutMetrics metrics = calculateWorkoutMetrics(workout);
        Duration duration = calculateWorkoutDuration(workout);

        if (duration.toMinutes() == 0) {
            return 0;
        }

        // Base metabolic rate during exercise (METs approximation)
        double strengthTrainingMETs = 6.0; // METs for strength training
        double bodyWeightKg = 70.0; // Default, could be user-specific

        // Calculate base calories from duration and METs
        double baseCalories = (strengthTrainingMETs * bodyWeightKg * duration.toMinutes()) / 60.0;

        // Add bonus calories based on volume (intensity factor)
        double volumeBonus = metrics.getTotalVolume().getValue().doubleValue() * 0.0005;

        // Add set completion bonus (work density factor)
        double completionBonus = metrics.getTotalSets() * 1.5;

        int totalCalories = (int) Math.round(baseCalories + volumeBonus + completionBonus);

        // Reasonable bounds
        return Math.max(0, Math.min(totalCalories, 2000));
    }

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
                .map(exercise -> exercise.getExerciseCategory())
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
                        .map(set -> set.getRestSeconds())
                        .filter(restSeconds -> restSeconds != null && restSeconds > 0)
                        .forEach(restTimes::add);
            }
        });

        return restTimes.isEmpty() ? 0.0 :
                restTimes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
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
