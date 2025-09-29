package com.muscledia.workout_service.domain.model;

import com.muscledia.workout_service.domain.vo.Volume;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
public class ExerciseData {
    String exerciseId;
    String exerciseName;
    String category;
    String primaryMuscleGroup;
    List<String> secondaryMuscleGroups;
    List<SetData> sets;
    Integer exerciseOrder;

    /**
     * Calculate total volume for this exercise using domain value objects
     */
    public Volume calculateVolume() {
        if (sets == null || sets.isEmpty()) {
            return Volume.zero();
        }

        return sets.stream()
                .filter(set -> set.getWeight() != null && set.getReps() != null)
                .map(set -> Volume.create(set.getWeight().multiply(
                        BigDecimal.valueOf(set.getReps()))))
                .reduce(Volume.zero(), Volume::add);
    }

    /**
     * Get total reps for this exercise
     */
    public int getTotalReps() {
        if (sets == null) {
            return 0;
        }
        return sets.stream()
                .filter(set -> set.getReps() != null)
                .mapToInt(SetData::getReps)
                .sum();
    }

    /**
     * Check if all sets are completed
     */
    public boolean isFullyCompleted() {
        if (sets == null || sets.isEmpty()) {
            return false;
        }
        return sets.stream().anyMatch(SetData::isCompleted);
    }

    /**
     * Get the heaviest weight used
     */
    public BigDecimal getMaxWeight() {
        if (sets == null || sets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return sets.stream()
                .map(SetData::getWeight)
                .filter(weight -> weight != null)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
}
