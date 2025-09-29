package com.muscledia.workout_service.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event triggered when a user completes an individual exercise.
 * This event is published by the workout service.
 */
@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class ExerciseCompletedEvent extends BaseEvent {

    /**
     * Unique identifier for the exercise
     */
    @NotBlank
    private String exerciseId;

    /**
     * Name of the exercise completed
     */
    @NotBlank
    private String exerciseName;

    /**
     * Category of exercise (e.g., "chest", "legs", "cardio")
     */
    @NotBlank
    private String exerciseCategory;

    /**
     * Workout this exercise belongs to
     */
    @NotBlank
    private String workoutId;

    /**
     * Number of sets completed
     */
    @Min(1)
    private Integer setsCompleted;

    /**
     * Total reps across all sets
     */
    @Min(1)
    private Integer totalReps;

    /**
     * Total volume for this exercise
     */
    private BigDecimal totalVolume;

    /**
     * Maximum weight used in this exercise
     */
    private BigDecimal maxWeight;

    /**
     * Primary muscle group targeted
     */
    private String primaryMuscleGroup;

    /**
     * Weight used (if applicable) - keeping for backward compatibility
     */
    private Double weight;

    /**
     * Unit for weight measurement
     */
    private String weightUnit;

    /**
     * Duration if it's a time-based exercise
     */
    private Integer durationSeconds;

    /**
     * Distance if it's a distance-based exercise
     */
    private Double distance;

    /**
     * Unit for distance measurement
     */
    private String distanceUnit;

    /**
     * Detailed set information
     * List of {reps: 12, weight: 135, restTime: 60}
     */
    private List<Map<String, Object>> setDetails;

    /**
     * Equipment used for the exercise
     */
    private String equipment;

    /**
     * Additional exercise metadata
     */
    private Map<String, Object> metadata;

    @Override
    public String getEventType() {
        return "EXERCISE_COMPLETED"; // Must match "name" in BaseEvent's @JsonSubTypes and KafkaConfig's type mapping
    }

    @Override
    public boolean isValid() {
        // Basic field validation
        if (exerciseName == null || exerciseName.trim().isEmpty() ||
                exerciseCategory == null || exerciseCategory.trim().isEmpty() ||
                workoutId == null || workoutId.trim().isEmpty()) {
            return false;
        }

        // For rep-based exercises (strength training)
        boolean hasValidRepData = setsCompleted != null && setsCompleted > 0 &&
                totalReps != null && totalReps > 0;

        // For duration-based exercises (cardio, warmup, etc.)
        boolean hasValidDurationData = durationSeconds != null && durationSeconds > 0;

        // For distance-based exercises
        boolean hasValidDistanceData = distance != null && distance > 0.0;

        // Exercise is valid if it has at least one valid performance metric
        return hasValidRepData || hasValidDurationData || hasValidDistanceData;
    }

    @Override
    public BaseEvent withNewTimestamp() {
        return this.toBuilder()
                .timestamp(Instant.now())
                .build();
    }
}
