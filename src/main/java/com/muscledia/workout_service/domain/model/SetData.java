package com.muscledia.workout_service.domain.model;

import com.muscledia.workout_service.domain.vo.Volume;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
public class SetData {
    Integer setNumber;
    BigDecimal weight;
    Integer reps;
    Integer durationSeconds;
    BigDecimal distanceMeters;
    Integer restSeconds;
    Integer rpe;
    boolean completed;
    boolean failure;
    boolean warmUp;
    String setType;
    LocalDateTime completedAt;

    /**
     * Calculate volume using domain value object
     */
    public Volume getVolume() {
        return Volume.of(weight, reps != null ? reps : 0);
    }

    /**
     * Check if this is a working set
     */
    public boolean isWorkingSet() {
        return !warmUp;
    }

    /**
     * Calculate estimated 1RM using Epley formula
     */
    public BigDecimal getEstimated1RM() {
        if (weight == null || reps == null || reps <= 0) {
            return BigDecimal.ZERO;
        }

        if (reps == 1) {
            return weight;
        }

        // Epley formula: weight * (1 + reps/30)
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, java.math.RoundingMode.HALF_UP)
        );

        return weight.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
