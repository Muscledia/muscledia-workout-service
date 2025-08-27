package com.muscledia.workout_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request to start a new workout session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to start a new workout session")
public class StartWorkoutRequest {
    @Schema(description = "Name for this workout session", example = "Morning Push Session")
    @NotBlank(message = "Workout name is required")
    @JsonProperty("workoutName")
    private String workoutName;

    @Schema(description = "Reference to workout plan template (optional)")
    @JsonProperty("workoutPlanId")
    private String workoutPlanId;

    private boolean useWorkoutPlan = true; // Auto-populate from plan
    private List<String> excludeExerciseIds; // Skip certain exercises
    private Map<String, Object> customizations; // Override plan defaults

    @Schema(description = "Type of workout", example = "STRENGTH",
            allowableValues = {"STRENGTH", "CARDIO", "FLEXIBILITY", "SPORTS", "MIXED"})
    @NotBlank(message = "Workout type is required")
    @JsonProperty("workoutType")
    private String workoutType;

    @Schema(description = "Location where workout will be performed", example = "Home Gym")
    private String location;

    @Schema(description = "Initial notes about the planned workout")
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Schema(description = "Tags for categorizing this workout")
    private List<String> tags;
}
