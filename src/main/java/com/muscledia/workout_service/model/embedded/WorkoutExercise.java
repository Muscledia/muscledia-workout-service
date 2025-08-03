package com.muscledia.workout_service.model.embedded;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutExercise {
    @Field("exercise_id")
    @NotBlank(message = "Exercise ID is required")
    private String exerciseId;

    @NotBlank(message = "Exercise name is required")
    private String exerciseName;

    @NotBlank(message = "Exercise category is required")
    private String exerciseCategory; // e.g., "Strength", "Cardio"

    @NotNull(message = "Number of sets is required")
    @Min(value = 1, message = "Must perform at least 1 set")
    @Max(value = 100, message = "Cannot exceed 100 sets per exercise")
    private Integer sets;

    @NotNull(message = "Number of reps is required")
    @Min(value = 1, message = "Must perform at least 1 rep")
    @Max(value = 1000, message = "Cannot exceed 1000 reps per set")
    private Integer reps;

    @PositiveOrZero(message = "Weight cannot be negative")
    private Double weight; // Max weight lifted for this exercise, or average

    private String weightUnit; // e.g., "kg", "lbs"

    @Min(value = 0, message = "Duration cannot be negative")
    private Integer durationSeconds; // For cardio/timed exercises

    @PositiveOrZero(message = "Distance cannot be negative")
    private Double distance; // For cardio
    private String distanceUnit; // e.g., "km", "miles"

    @Field("exercise_order")
    @Min(value = 1, message = "Exercise order must be at least 1")
    @Max(value = 50, message = "Exercise order cannot exceed 50")
    private Integer order;

    @Size(max = 500, message = "Exercise notes cannot exceed 500 characters")
    private String notes;
}