package com.muscledia.workout_service.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object representing a single set's performance
 * Immutable and self-calculating
 */
public class SetPerformance {

    private final BigDecimal weight;
    private final Integer reps;
    private final Volume volume;
    private final BigDecimal estimated1RM;

    private SetPerformance(BigDecimal weight, Integer reps) {
        this.weight = weight != null ? weight : BigDecimal.ZERO;
        this.reps = reps != null ? reps : 0;
        this.volume = Volume.of(this.weight, this.reps);
        this.estimated1RM = calculateEstimated1RM();
    }

    public static SetPerformance of(BigDecimal weight, Integer reps) {
        return new SetPerformance(weight, reps);
    }

    public static SetPerformance empty() {
        return new SetPerformance(BigDecimal.ZERO, 0);
    }

    private BigDecimal calculateEstimated1RM() {
        if (weight == null || reps == null || reps <= 0) {
            return BigDecimal.ZERO;
        }

        if (reps == 1) {
            return weight;
        }

        // Epley formula: weight * (1 + reps/30)
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP)
        );

        return weight.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isValid() {
        return weight.compareTo(BigDecimal.ZERO) > 0 && reps > 0;
    }

    // Getters
    public BigDecimal getWeight() { return weight; }
    public Integer getReps() { return reps; }
    public Volume getVolume() { return volume; }
    public BigDecimal getEstimated1RM() { return estimated1RM; }
}
