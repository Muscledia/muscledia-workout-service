package com.muscledia.workout_service.dto.request;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request to start a workout from a saved workout plan with customizations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to start a workout from a saved workout plan")
public class StartWorkoutFromPlanRequest {

    @Schema(description = "Custom name for this workout session", example = "Upper Body Power Session")
    @JsonProperty("workoutName")
    private String workoutName;

    @Schema(description = "Location where workout will be performed", example = "Home Gym")
    private String location;

    @Schema(description = "Additional notes for this workout session")
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Schema(description = "Tags for categorizing this workout")
    private List<String> tags;

    @Schema(description = "Exercise IDs to exclude from the plan",
            example = "[\"507f1f77bcf86cd799439012\"]")
    @JsonProperty("excludeExerciseIds")
    private List<String> excludeExerciseIds;

    @Schema(description = "Customizations to apply to the plan exercises")
    private Map<String, Object> customizations;
}
