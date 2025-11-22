package com.muscledia.workout_service.domain.service;

import com.muscledia.workout_service.domain.vo.SetPerformance;
import com.muscledia.workout_service.domain.vo.Volume;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.model.enums.SetType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Domain Service for exercise performance analysis
 * Handles all exercise-specific calculations
 */
@Service
public class ExercisePerformanceService {

    /**
     * Calculate total volume for an exercise
     */
    public Volume calculateExerciseVolume(WorkoutExercise exercise) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return Volume.zero();
        }

        return exercise.getSets().stream()
                .map(set -> Volume.of(set.getWeightKg(), set.getReps() != null ? set.getReps() : 0))
                .reduce(Volume.zero(), Volume::add);
    }

    /**
     * Calculate total reps for an exercise
     */
    public int calculateTotalReps(WorkoutExercise exercise) {
        if (exercise.getSets() == null) {
            return 0;
        }

        return exercise.getSets().stream()
                .filter(set -> set.getReps() != null)
                .mapToInt(WorkoutSet::getReps)
                .sum();
    }

    /**
     * Find the best set performance (highest weight)
     */
    public SetPerformance findBestSetPerformance(WorkoutExercise exercise) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return SetPerformance.empty();
        }

        return exercise.getSets().stream()
                .filter(set -> set.getWeightKg() != null && set.getReps() != null)
                .max(Comparator.comparing(WorkoutSet::getWeightKg))
                .map(set -> SetPerformance.of(set.getWeightKg(), set.getReps()))
                .orElse(SetPerformance.empty());
    }

    /**
     * Calculate estimated 1RM for exercise (best set)
     */
    public BigDecimal calculateEstimated1RM(WorkoutExercise exercise) {
        SetPerformance bestSet = findBestSetPerformance(exercise);
        return bestSet.getEstimated1RM();
    }

    /**
     * Calculate average weight across all working sets
     */
    public BigDecimal calculateAverageWeight(WorkoutExercise exercise) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> weights = exercise.getSets().stream()
                .map(WorkoutSet::getWeightKg)
                .filter(Objects::nonNull)
                .filter(weight -> weight.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (weights.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = weights.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(weights.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate average RPE for exercise
     */
    public OptionalDouble calculateAverageRpe(WorkoutExercise exercise) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return OptionalDouble.empty();
        }

        return exercise.getSets().stream()
                .filter(set -> set.getRpe() != null)
                .mapToInt(WorkoutSet::getRpe)
                .average();
    }

    /**
     * Get exercise completion statistics
     */
    public ExerciseCompletionStats getCompletionStats(WorkoutExercise exercise) {
        if (exercise.getSets() == null) {
            return new ExerciseCompletionStats(0, 0, 0);
        }

        int totalSets = exercise.getSets().size();
        int completedSets = (int) exercise.getSets().stream()
                .filter(set -> Boolean.TRUE.equals(set.getCompleted()))
                .count();
        int failedSets = (int) exercise.getSets().stream()
                .filter(set -> set.getSetType() == SetType.FAILURE)
                .count();

        return new ExerciseCompletionStats(totalSets, completedSets, failedSets);
    }

    /**
     * Check if exercise is strength-based
     */
    public boolean isStrengthExercise(WorkoutExercise exercise) {
        return exercise.getSets() != null && exercise.getSets().stream()
                .anyMatch(set -> set.getWeightKg() != null && set.getReps() != null);
    }

    /**
     * Check if exercise is cardio-based
     */
    public boolean isCardioExercise(WorkoutExercise exercise) {
        return exercise.getSets() != null && exercise.getSets().stream()
                .anyMatch(set -> set.getDurationSeconds() != null || set.getDistanceMeters() != null);
    }

    /**
     * Inner class for completion statistics
     */
    public static class ExerciseCompletionStats {
        private final int totalSets;
        private final int completedSets;
        private final int failedSets;

        public ExerciseCompletionStats(int totalSets, int completedSets, int failedSets) {
            this.totalSets = totalSets;
            this.completedSets = completedSets;
            this.failedSets = failedSets;
        }

        public int getTotalSets() { return totalSets; }
        public int getCompletedSets() { return completedSets; }
        public int getFailedSets() { return failedSets; }
        public double getCompletionRate() {
            return totalSets > 0 ? (double) completedSets / totalSets : 0.0;
        }
        public boolean isFullyCompleted() { return completedSets == totalSets && failedSets == 0; }
    }
}
