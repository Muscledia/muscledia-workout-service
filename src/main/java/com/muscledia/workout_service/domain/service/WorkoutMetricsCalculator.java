package com.muscledia.workout_service.domain.service;

import com.muscledia.workout_service.domain.model.WorkoutData;
import com.muscledia.workout_service.domain.vo.Volume;
import com.muscledia.workout_service.domain.model.SetData;
import com.muscledia.workout_service.domain.vo.WorkoutMetrics;
import com.muscledia.workout_service.domain.model.ExerciseData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Pure Domain Service for Workout Metrics Calculation
 * Works exclusively with domain objects (no infrastructure dependencies)
 * Follows DDD principle of encapsulating domain logic in domain services
 */
@Service
@Slf4j
public class WorkoutMetricsCalculator {
    /**
     * Calculate comprehensive workout metrics from domain workout data
     */
    public WorkoutMetrics calculateWorkoutMetrics(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            log.debug("Empty workout data provided, returning empty metrics");
            return WorkoutMetrics.empty();
        }

        log.debug("Calculating metrics for workout {} with {} exercises",
                workoutData.getId(), workoutData.getExercises().size());

        return WorkoutMetrics.calculateFromExerciseData(workoutData.getExercises());
    }

    /**
     * Calculate total workout volume using domain value objects
     */
    public Volume calculateTotalVolume(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return Volume.zero();
        }

        return workoutData.getExercises().stream()
                .map(ExerciseData::calculateVolume)
                .reduce(Volume.zero(), Volume::add);
    }

    /**
     * Calculate total duration from domain data
     */
    public Duration calculateWorkoutDuration(WorkoutData workoutData) {
        if (workoutData == null) {
            return Duration.ZERO;
        }

        if (workoutData.getStartedAt() != null && workoutData.getCompletedAt() != null) {
            return Duration.between(workoutData.getStartedAt(), workoutData.getCompletedAt());
        }

        return Duration.ZERO;
    }

    /**
     * Calculate total sets across all exercises
     */
    public int calculateTotalSets(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return 0;
        }

        return workoutData.getExercises().stream()
                .mapToInt(exercise -> exercise.getSets() != null ? exercise.getSets().size() : 0)
                .sum();
    }

    /**
     * Calculate total reps across all exercises
     */
    public int calculateTotalReps(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return 0;
        }

        return workoutData.getExercises().stream()
                .mapToInt(ExerciseData::getTotalReps)
                .sum();
    }

    /**
     * Count completed exercises
     */
    public int countCompletedExercises(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return 0;
        }

        return (int) workoutData.getExercises().stream()
                .filter(ExerciseData::isFullyCompleted)
                .count();
    }

    /**
     * Calculate workout completion percentage
     */
    public double calculateCompletionPercentage(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return 0.0;
        }

        int totalExercises = workoutData.getExercises().size();
        int completedExercises = countCompletedExercises(workoutData);

        return totalExercises > 0 ? (double) completedExercises / totalExercises * 100.0 : 0.0;
    }

    /**
     * Get all worked muscle groups from domain data
     */
    public List<String> getWorkedMuscleGroups(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return List.of();
        }

        return workoutData.getExercises().stream()
                .flatMap(exercise -> {
                    var muscleGroups = new java.util.ArrayList<String>();

                    if (exercise.getPrimaryMuscleGroup() != null &&
                            !exercise.getPrimaryMuscleGroup().trim().isEmpty()) {
                        muscleGroups.add(exercise.getPrimaryMuscleGroup().toLowerCase());
                    }

                    if (exercise.getSecondaryMuscleGroups() != null) {
                        exercise.getSecondaryMuscleGroups().stream()
                                .filter(group -> group != null && !group.trim().isEmpty())
                                .map(String::toLowerCase)
                                .forEach(muscleGroups::add);
                    }

                    return muscleGroups.stream();
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Estimate calories burned using domain data
     */
    public Integer estimateCaloriesBurned(WorkoutData workoutData) {
        if (workoutData == null) {
            return 0;
        }

        Duration duration = calculateWorkoutDuration(workoutData);
        Volume totalVolume = calculateTotalVolume(workoutData);
        int totalSets = calculateTotalSets(workoutData);

        if (duration.toMinutes() == 0) {
            return 0;
        }

        // Base metabolic rate during exercise (METs approximation)
        double strengthTrainingMETs = 6.0; // METs for strength training
        double bodyWeightKg = 70.0; // Default - would be user-specific in real implementation

        // Calculate base calories from duration and METs
        double baseCalories = (strengthTrainingMETs * bodyWeightKg * duration.toMinutes()) / 60.0;

        // Add bonus calories based on volume (intensity factor)
        double volumeBonus = totalVolume.getValue().doubleValue() * 0.0005;

        // Add set completion bonus (work density factor)
        double completionBonus = totalSets * 1.5;

        int totalCalories = (int) Math.round(baseCalories + volumeBonus + completionBonus);

        // Reasonable bounds
        return Math.max(0, Math.min(totalCalories, 2000));
    }

    /**
     * Calculate workout intensity score (0-10 scale)
     */
    public Double calculateIntensityScore(WorkoutData workoutData) {
        if (workoutData == null) {
            return 0.0;
        }

        Duration duration = calculateWorkoutDuration(workoutData);
        Volume totalVolume = calculateTotalVolume(workoutData);
        int totalSets = calculateTotalSets(workoutData);
        double completionRate = calculateCompletionPercentage(workoutData) / 100.0;

        if (duration.toMinutes() == 0 || totalVolume.isZero()) {
            return 0.0;
        }

        // Volume per minute
        double volumePerMinute = totalVolume.getValue().doubleValue() / duration.toMinutes();

        // Sets per minute (density)
        double setsPerMinute = (double) totalSets / duration.toMinutes();

        // Weighted score (0-10 scale)
        double intensityScore = Math.min(10.0,
                (volumePerMinute * 0.01) +
                        (setsPerMinute * 2.0) +
                        (completionRate * 3.0)
        );

        return Math.round(intensityScore * 10.0) / 10.0;
    }

    /**
     * Calculate average rest time between sets
     */
    public Double calculateAverageRestTime(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return 0.0;
        }

        List<Integer> restTimes = workoutData.getExercises().stream()
                .flatMap(exercise -> exercise.getSets() != null ?
                        exercise.getSets().stream() : java.util.stream.Stream.empty())
                .map(SetData::getRestSeconds)
                .filter(restSeconds -> restSeconds != null && restSeconds > 0)
                .toList();

        return restTimes.isEmpty() ? 0.0 :
                restTimes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * Get exercise categories from domain data
     */
    public List<String> getExerciseCategories(WorkoutData workoutData) {
        if (workoutData == null || !workoutData.hasExercises()) {
            return List.of();
        }

        return workoutData.getExercises().stream()
                .map(ExerciseData::getCategory)
                .filter(category -> category != null && !category.trim().isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Calculate per-exercise metrics
     */
    public ExerciseMetrics calculateExerciseMetrics(ExerciseData exerciseData) {
        if (exerciseData == null) {
            return ExerciseMetrics.empty();
        }

        Volume totalVolume = exerciseData.calculateVolume();
        int totalReps = exerciseData.getTotalReps();
        BigDecimal maxWeight = exerciseData.getMaxWeight();
        boolean isCompleted = exerciseData.isFullyCompleted();

        OptionalDouble avgRpe = exerciseData.getSets() != null ?
                exerciseData.getSets().stream()
                        .filter(set -> set.getRpe() != null)
                        .mapToInt(SetData::getRpe)
                        .average() : OptionalDouble.empty();

        return ExerciseMetrics.builder()
                .exerciseId(exerciseData.getExerciseId())
                .exerciseName(exerciseData.getExerciseName())
                .totalVolume(totalVolume)
                .totalReps(totalReps)
                .maxWeight(maxWeight)
                .setCount(exerciseData.getSets() != null ? exerciseData.getSets().size() : 0)
                .averageRpe(avgRpe.isPresent() ? avgRpe.getAsDouble() : null)
                .isCompleted(isCompleted)
                .build();
    }

    /**
     * Value object for individual exercise metrics
     */
    @lombok.Builder
    @lombok.Data
    public static class ExerciseMetrics {
        private final String exerciseId;
        private final String exerciseName;
        private final Volume totalVolume;
        private final int totalReps;
        private final BigDecimal maxWeight;
        private final int setCount;
        private final Double averageRpe;
        private final boolean isCompleted;

        public static ExerciseMetrics empty() {
            return ExerciseMetrics.builder()
                    .exerciseId("")
                    .exerciseName("")
                    .totalVolume(Volume.zero())
                    .totalReps(0)
                    .maxWeight(BigDecimal.ZERO)
                    .setCount(0)
                    .averageRpe(null)
                    .isCompleted(false)
                    .build();
        }
    }

    /**
     * Calculate workout efficiency metrics
     */
    public WorkoutEfficiencyMetrics calculateEfficiencyMetrics(WorkoutData workoutData) {
        if (workoutData == null) {
            return WorkoutEfficiencyMetrics.empty();
        }

        Duration duration = calculateWorkoutDuration(workoutData);
        if (duration.toMinutes() == 0) {
            return WorkoutEfficiencyMetrics.empty();
        }

        Volume totalVolume = calculateTotalVolume(workoutData);
        int totalSets = calculateTotalSets(workoutData);
        int totalReps = calculateTotalReps(workoutData);

        double volumePerMinute = totalVolume.getValue().doubleValue() / duration.toMinutes();
        double setsPerMinute = (double) totalSets / duration.toMinutes();
        double repsPerMinute = (double) totalReps / duration.toMinutes();

        return WorkoutEfficiencyMetrics.builder()
                .volumePerMinute(volumePerMinute)
                .setsPerMinute(setsPerMinute)
                .repsPerMinute(repsPerMinute)
                .totalDuration(duration)
                .build();
    }

    /**
     * Value object for efficiency metrics
     */
    @lombok.Builder
    @lombok.Data
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
