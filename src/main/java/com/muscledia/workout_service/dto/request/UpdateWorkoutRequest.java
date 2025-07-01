package com.muscledia.workout_service.dto.request;

import com.muscledia.workout_service.dto.request.embedded.WorkoutExerciseRequest;
import com.muscledia.workout_service.validation.ValidWorkout;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ValidWorkout
@Schema(description = "Request object for updating an existing workout")
public class UpdateWorkoutRequest {

    @Schema(description = "Date and time when the workout was performed", example = "2024-01-15T10:30:00")
    @PastOrPresent(message = "Workout date cannot be in the future")
    private LocalDateTime workoutDate;

    @Schema(description = "Optional reference to the workout plan used", example = "507f1f77bcf86cd799439011")
    private String workoutPlanId;

    @Schema(description = "Duration of the workout in minutes", example = "60", minimum = "1", maximum = "600")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 600, message = "Duration cannot exceed 600 minutes (10 hours)")
    private Integer durationMinutes;

    @Schema(description = "Total volume lifted during the workout", example = "5250.5", minimum = "0")
    @DecimalMin(value = "0.0", message = "Total volume cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Total volume must have at most 10 integer digits and 2 decimal places")
    private BigDecimal totalVolume;

    @Schema(description = "Optional notes about the workout", example = "Great workout today! Felt strong on bench press.")
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    @Schema(description = "List of exercises performed in the workout")
    @Size(max = 50, message = "Cannot have more than 50 exercises in a single workout")
    @Valid
    private List<WorkoutExerciseRequest> exercises;
}