package com.muscledia.workout_service.event;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event triggered when a user completes a workout.
 * This event is published by the workout service.
 */
@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor // Explicitly add NoArgsConstructor for Jackson deserialization
public class WorkoutCompletedEvent extends BaseEvent {
    /**
     * Unique identifier for the workout
     */
    @NotBlank
    private String workoutId;

    /**
     * Type of workout (e.g., "strength", "cardio", "hiit")
     */
    @NotBlank
    private String workoutType;

    /**
     * Duration of workout in minutes
     */
    @Min(1)
    private Integer durationMinutes;

    /**
     * Calories burned during workout
     */
    @Min(0)
    private Integer caloriesBurned;

    /**
     * Number of exercises completed
     */
    @Min(1)
    private Integer exercisesCompleted;

    /**
     * Total sets completed across all exercises
     */
    @Min(1)
    private Integer totalSets;

    /**
     * Total reps completed across all exercises
     */
    @Min(1)
    private Integer totalReps;

    /**
     * Total volume (weight × reps) across all exercises
     */
    private BigDecimal totalVolume;

    /**
     * List of muscle groups worked in this workout
     */
    private List<String> workedMuscleGroups;

    /**
     * When the workout was started
     */
    @NotNull
    private Instant workoutStartTime; // Use Instant

    /**
     * When the workout was completed
     */
    @NotNull
    private Instant workoutEndTime; // Use Instant

    /**
     * Additional metadata about the workout
     * e.g., {"difficulty": "intermediate", "category": "upper-body"}
     */
    private Map<String, Object> metadata;

    @Override
    public String getEventType() {
        return "WORKOUT_COMPLETED"; // Must match "name" in BaseEvent's @JsonSubTypes and KafkaConfig's type mapping
    }

    @Override
    public boolean isValid() {
        // Includes BaseEvent validation implicitly through Lombok's @Data on superclass
        return workoutId != null && !workoutId.trim().isEmpty()
                && workoutType != null && !workoutType.trim().isEmpty()
                && durationMinutes != null && durationMinutes > 0
                && exercisesCompleted != null && exercisesCompleted > 0
                && totalSets != null && totalSets > 0 // Ensure totalSets is validated
                && totalReps != null && totalReps > 0 // Ensure totalReps is validated
                && workoutStartTime != null
                && workoutEndTime != null
                && workoutEndTime.isAfter(workoutStartTime);
    }

    @Override
    public BaseEvent withNewTimestamp() {
        return this.toBuilder()
                .timestamp(Instant.now())
                .build();
    }
}
