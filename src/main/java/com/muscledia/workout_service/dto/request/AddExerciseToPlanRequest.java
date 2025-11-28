package com.muscledia.workout_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddExerciseToPlanRequest {

    @NotBlank(message = "Exercise ID is required")
    private String exerciseId;

    @Min(value = 1, message = "At least 1 set required")
    @Max(value = 10, message = "Maximum 10 sets allowed")
    private Integer numberOfSets = 3;

    private Integer targetReps;
    private Integer repRangeStart;
    private Integer repRangeEnd;
    private Double targetWeight;

    @Min(value = 0, message = "Rest time cannot be negative")
    @Max(value = 600, message = "Rest time cannot exceed 10 minutes")
    private Integer restSeconds = 90;

    @Size(max = 200, message = "Notes must be less than 200 characters")
    private String notes;
}
