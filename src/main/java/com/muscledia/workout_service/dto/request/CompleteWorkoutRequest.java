package com.muscledia.workout_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for completing a workout
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to complete a workout session")
public class CompleteWorkoutRequest {
    @Schema(description = "Overall workout rating (1-10)", example = "8")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 10, message = "Rating cannot exceed 10")
    private Integer rating;

    @Schema(description = "Final notes about the workout")
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    @Schema(description = "Estimated calories burned")
    @Min(value = 0, message = "Calories cannot be negative")
    @JsonProperty("caloriesBurned")
    private Integer caloriesBurned;

    @Schema(description = "Any additional tags to add")
    private List<String> additionalTags;
}
