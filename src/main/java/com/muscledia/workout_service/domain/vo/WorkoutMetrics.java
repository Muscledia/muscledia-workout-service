package com.muscledia.workout_service.domain.vo;

import com.muscledia.workout_service.model.embedded.WorkoutExercise;

import java.util.List;


/**
 * Value Object containing calculated workout metrics
 * Immutable and derived from exercise data
 */
public class WorkoutMetrics {
    private final Volume totalVolume;
    private final int totalSets;
    private final int totalReps;
    private final int completedExercises;

    private WorkoutMetrics(Volume totalVolume, int totalSets, int totalReps, int completedExercises) {
        this.totalVolume = totalVolume != null ? totalVolume : Volume.zero();
        this.totalSets = Math.max(0, totalSets);
        this.totalReps = Math.max(0, totalReps);
        this.completedExercises = Math.max(0, completedExercises);
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

                for (var set : exercise.getSets()) {
                    if (set.getReps() != null) {
                        totalReps += set.getReps();
                    }
                    if (set.getWeightKg() != null && set.getReps() != null) {
                        totalVolume = totalVolume.add(Volume.of(set.getWeightKg(), set.getReps()));
                    }
                }

                if (exercise.isFullyCompleted()) {
                    completedExercises++;
                }
            }
        }

        return new WorkoutMetrics(totalVolume, totalSets, totalReps, completedExercises);
    }

    public static WorkoutMetrics empty() {
        return new WorkoutMetrics(Volume.zero(), 0, 0, 0);
    }

    // Getters
    public Volume getTotalVolume() { return totalVolume; }
    public int getTotalSets() { return totalSets; }
    public int getTotalReps() { return totalReps; }
    public int getCompletedExercises() { return completedExercises; }

    public boolean isEmpty() {
        return totalSets == 0 && totalReps == 0 && totalVolume.isZero();
    }

    @Override
    public String toString() {
        return String.format("WorkoutMetrics{volume=%s, sets=%d, reps=%d, completed=%d}",
                totalVolume, totalSets, totalReps, completedExercises);
    }
}
