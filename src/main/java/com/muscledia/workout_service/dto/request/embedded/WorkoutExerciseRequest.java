package com.muscledia.workout_service.dto.request.embedded;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request object for an exercise within a workout")
public class WorkoutExerciseRequest {

    @Schema(description = "ID of the exercise being performed", example = "507f1f77bcf86cd799439011", required = true)
    @NotBlank(message = "Exercise ID is required")
    private String exerciseId;

    @Schema(description = "Number of sets performed", example = "3", minimum = "1", maximum = "100")
    @NotNull(message = "Number of sets is required")
    @Min(value = 1, message = "Must perform at least 1 set")
    @Max(value = 100, message = "Cannot exceed 100 sets per exercise")
    private Integer sets;

    @Schema(description = "Number of repetitions per set", example = "10", minimum = "1", maximum = "1000")
    @NotNull(message = "Number of reps is required")
    @Min(value = 1, message = "Must perform at least 1 rep")
    @Max(value = 1000, message = "Cannot exceed 1000 reps per set")
    private Integer reps;

    @Schema(description = "Weight used for the exercise in the user's preferred unit", example = "135.5", minimum = "0")
    @NotNull(message = "Weight is required")
    @PositiveOrZero(message = "Weight cannot be negative")
    private Double weight; // Max weight lifted for this exercise, or average

    private String weightUnit; // e.g., "kg", "lbs"

    @Schema(description = "Order of the exercise in the workout", example = "1", minimum = "1")
    @Min(value = 1, message = "Exercise order must be at least 1")
    @Max(value = 50, message = "Exercise order cannot exceed 50")
    private Integer order;

    @Schema(description = "Optional notes about this specific exercise", example = "Felt good, could increase weight next time")
    @Size(max = 500, message = "Exercise notes cannot exceed 500 characters")
    private String notes;
}