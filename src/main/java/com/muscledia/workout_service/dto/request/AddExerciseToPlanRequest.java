package com.muscledia.workout_service.dto.request;

import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for adding exercise to active workout session
 * Maps to WorkoutExercise entity
 */
@Data
public class AddExerciseToPlanRequest {
    @NotBlank(message = "Exercise ID is required")
    private String exerciseId;

    @NotBlank(message = "Exercise name is required")
    private String exerciseName;

    private String notes;

    // Optional exercise metadata for display
    private String exerciseCategory;
    private String primaryMuscleGroup;
    private List<String> secondaryMuscleGroups;
    private String equipment;

    // Denormalized exercise properties
    private String bodyPart;
    private String targetMuscle;
    private ExerciseDifficulty difficulty;
    private ExerciseCategory category;
    private String description;

    @Min(value = 1, message = "Number of sets must be at least 1")
    private Integer numberOfSets;

    @Min(value = 1, message = "Target reps must be positive")
    private Integer targetReps;

    private Integer repRangeStart;

    private Integer repRangeEnd;

    private Double targetWeight; // Using BigDecimal to match domain entities

    @Min(value = 0, message = "Rest seconds cannot be negative")
    private Integer restSeconds;

    // Sets for this exercise
    private List<WorkoutSetRequest> sets;

    /**
     * Nested DTO for workout sets
     */
    @Data
    public static class WorkoutSetRequest {
        @NotNull(message = "Set number is required")
        private Integer setNumber;

        private String setType; // NORMAL, WARMUP, DROP, FAILURE

        private Double weightKg;
        private Integer reps;
        private Integer durationSeconds;
        private Double distanceMeters;
        private Integer rpe;

        // Default to incomplete when adding
        private Boolean completed = false;
    }
}
