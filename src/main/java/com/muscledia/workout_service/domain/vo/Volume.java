package com.muscledia.workout_service.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;


/**
 * Value Object representing workout volume (weight × reps)
 * Immutable and self-validating
 */
public class Volume {

    private final BigDecimal value;

    private Volume(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Volume value cannot be null");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Volume cannot be negative");
        }
        this.value = value.setScale(2, RoundingMode.HALF_UP);
    }

    public static Volume of(BigDecimal weight, int reps) {
        if (weight == null || reps < 0) {
            return zero();
        }
        return new Volume(weight.multiply(BigDecimal.valueOf(reps)));
    }

    public static Volume of(BigDecimal value) {
        return new Volume(value);
    }

    public static Volume zero() {
        return new Volume(BigDecimal.ZERO);
    }

    public Volume add(Volume other) {
        return new Volume(this.value.add(other.value));
    }

    public Volume multiply(int factor) {
        return new Volume(this.value.multiply(BigDecimal.valueOf(factor)));
    }

    public BigDecimal getValue() {
        return value;
    }

    public boolean isZero() {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Volume)) return false;
        Volume volume = (Volume) obj;
        return Objects.equals(value, volume.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value + " kg·reps";
    }
}
