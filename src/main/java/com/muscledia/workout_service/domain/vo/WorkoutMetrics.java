package com.muscledia.workout_service.domain.vo;

import com.muscledia.workout_service.domain.model.ExerciseData;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


/**
 * Value Object containing calculated workout metrics
 * Immutable and derived from exercise data
 */
@Getter
@Data
@Builder
@Setter
public class WorkoutMetrics {
    // Getters
    private final Volume totalVolume;
    private final int totalSets;
    private final int totalReps;
    private final int completedExercises;
    private final boolean isEmpty;

    /**
     * Create empty metrics
     */
    public static WorkoutMetrics empty() {
        return WorkoutMetrics.builder()
                .totalVolume(Volume.zero())
                .totalSets(0)
                .totalReps(0)
                .completedExercises(0)
                .isEmpty(true)
                .build();
    }


    public static WorkoutMetrics calculate(List<WorkoutExercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return empty();
        }

        Volume totalVolume = Volume.zero();
        int totalSets = 0;
        int totalReps = 0;
        int completedExercises = 0;

        for (WorkoutExercise exercise : exercises) {
            if (exercise.getSets() != null) {
                totalSets += exercise.getSets().size();

                Volume exerciseVolume = Volume.zero();
                int exerciseReps = 0;
                boolean hasCompletedSet = false;

                for (var set : exercise.getSets()) {
                    if (set.getWeightKg() != null && set.getReps() != null) {
                        Volume setVolume = Volume.create(set.getWeightKg().multiply(
                                java.math.BigDecimal.valueOf(set.getReps())));
                        exerciseVolume = exerciseVolume.add(setVolume);
                        exerciseReps += set.getReps();
                    }

                    if (Boolean.TRUE.equals(set.getCompleted())) {
                        hasCompletedSet = true;
                    }
                }

                totalVolume = totalVolume.add(exerciseVolume);
                totalReps += exerciseReps;

                if (hasCompletedSet) {
                    completedExercises++;
                }
            }
        }

        return WorkoutMetrics.builder()
                .totalVolume(totalVolume)
                .totalSets(totalSets)
                .totalReps(totalReps)
                .completedExercises(completedExercises)
                .isEmpty(false)
                .build();
    }

    /**
     * NEW: Calculate from ExerciseData (domain objects)
     * This is the DDD-compliant version
     */
    public static WorkoutMetrics calculateFromExerciseData(List<ExerciseData> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return empty();
        }

        Volume totalVolume = Volume.zero();
        int totalSets = 0;
        int totalReps = 0;
        int completedExercises = 0;

        for (ExerciseData exercise : exercises) {
            if (exercise.getSets() != null) {
                totalSets += exercise.getSets().size();

                // Use domain object methods
                Volume exerciseVolume = exercise.calculateVolume();
                int exerciseReps = exercise.getTotalReps();
                boolean isCompleted = exercise.isFullyCompleted();

                totalVolume = totalVolume.add(exerciseVolume);
                totalReps += exerciseReps;

                if (isCompleted) {
                    completedExercises++;
                }
            }
        }

        return WorkoutMetrics.builder()
                .totalVolume(totalVolume)
                .totalSets(totalSets)
                .totalReps(totalReps)
                .completedExercises(completedExercises)
                .isEmpty(false)
                .build();
    }

    /**
     * Check if metrics are empty/zero
     */
    public boolean isEmpty() {
        return isEmpty || (totalVolume.isZero() && totalSets == 0 && totalReps == 0);
    }

    /**
     * Get completion percentage
     */
    public double getCompletionPercentage() {
        if (totalSets == 0) {
            return 0.0;
        }
        // This is a simplified calculation - in reality you might want to track completed sets
        return completedExercises > 0 ? 100.0 : 0.0;
    }

    /**
     * Get volume per set average
     */
    public Volume getAverageVolumePerSet() {
        if (totalSets == 0) {
            return Volume.zero();
        }
        return Volume.create(totalVolume.getValue().divide(
                java.math.BigDecimal.valueOf(totalSets), 2, java.math.RoundingMode.HALF_UP));
    }

    /**
     * Get reps per set average
     */
    public double getAverageRepsPerSet() {
        if (totalSets == 0) {
            return 0.0;
        }
        return (double) totalReps / totalSets;
    }
}
