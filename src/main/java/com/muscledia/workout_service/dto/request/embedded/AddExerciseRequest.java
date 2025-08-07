package com.muscledia.workout_service.dto.request.embedded;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to add an exercise to an active workout
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to add an exercise to an active workout")
public class AddExerciseRequest {
    @Schema(description = "Exercise ID from exercise database", example = "507f1f77bcf86cd799439011")
    @NotBlank(message = "Exercise ID is required")
    @JsonProperty("exerciseId")
    private String exerciseId;

    @Schema(description = "Exercise name", example = "Bench Press")
    @NotBlank(message = "Exercise name is required")
    @JsonProperty("exerciseName")
    private String exerciseName;

    @Schema(description = "Exercise category", example = "STRENGTH")
    @NotBlank(message = "Exercise category is required")
    @JsonProperty("exerciseCategory")
    private String exerciseCategory;

    @Schema(description = "Primary muscle group", example = "chest")
    @JsonProperty("primaryMuscleGroup")
    private String primaryMuscleGroup;

    @Schema(description = "Secondary muscle groups")
    @JsonProperty("secondaryMuscleGroups")
    private List<String> secondaryMuscleGroups;

    @Schema(description = "Equipment used", example = "barbell")
    private String equipment;

    @Schema(description = "Notes for this exercise")
    @Size(max = 500, message = "Exercise notes cannot exceed 500 characters")
    private String notes;
}
