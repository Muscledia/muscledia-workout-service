package com.muscledia.workout_service.event;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event triggered when a user achieves a new personal record.
 * This event is published by the workout service when PRs are detected.
 */
@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PersonalRecordEvent extends BaseEvent {
    /**
     * Exercise for which the PR was achieved
     */
    @NotBlank
    private String exerciseName;

    /**
     * Exercise ID
     */
    @NotBlank
    private String exerciseId;

    /**
     * Type of personal record (e.g., "MAX_WEIGHT", "MAX_VOLUME", "MAX_REPS", "ESTIMATED_1RM")
     */
    @NotBlank
    private String recordType;

    /**
     * New record value
     */
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal newValue;

    /**
     * Previous record value (for improvement calculation)
     */
    private BigDecimal previousValue;

    /**
     * Unit of measurement (e.g., "kg", "reps")
     */
    @NotBlank
    private String unit;

    /**
     * Workout ID where this PR was achieved
     */
    @NotBlank
    private String workoutId;

    /**
     * Number of reps performed (for weight-based PRs)
     */
    private Integer reps;

    /**
     * Weight used (for weight-based PRs)
     */
    private BigDecimal weight;

    /**
     * When the PR was achieved
     */
    @NotNull
    private Instant achievedAt;

    /**
     * Improvement percentage over previous record
     */
    private Double improvementPercentage;

    @Override
    public String getEventType() {
        return "PERSONAL_RECORD";
    }

    @Override
    public boolean isValid() {
        return exerciseName != null && !exerciseName.trim().isEmpty()
                && exerciseId != null && !exerciseId.trim().isEmpty()
                && recordType != null && !recordType.trim().isEmpty()
                && newValue != null && newValue.compareTo(BigDecimal.ZERO) > 0
                && unit != null && !unit.trim().isEmpty()
                && achievedAt != null;
    }

    @Override
    public BaseEvent withNewTimestamp() {
        return this.toBuilder()
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Calculate improvement percentage over previous record
     */
    public double getImprovementPercentage() {
        if (previousValue == null || previousValue.compareTo(BigDecimal.ZERO) <= 0) {
            return 100.0; // First PR is 100% improvement
        }

        return newValue.subtract(previousValue)
                .divide(previousValue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * Determine if this PR qualifies for milestone badges
     */
    public boolean isMilestonePR() {
        return switch (unit.toLowerCase()) {
            case "kg" -> isWeightMilestone();
            case "reps" -> isRepsMilestone();
            default -> false;
        };
    }

    private boolean isWeightMilestone() {
        // Check for common weight milestones (100, 150, 200 kg etc.)
        double value = newValue.doubleValue();
        return value >= 100 && value % 50 == 0;
    }

    private boolean isRepsMilestone() {
        // Check for rep milestones
        double value = newValue.doubleValue();
        return value >= 10 && value % 10 == 0;
    }
}
