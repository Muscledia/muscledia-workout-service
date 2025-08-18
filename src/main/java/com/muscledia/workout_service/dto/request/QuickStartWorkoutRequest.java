package com.muscledia.workout_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for quick start workout options
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Quick start workout request")
public class QuickStartWorkoutRequest {
    @Schema(description = "Preferred workout type", example = "STRENGTH")
    @JsonProperty("workoutType")
    private String workoutType;

    @Schema(description = "Available time in minutes", example = "45")
    @JsonProperty("availableTimeMinutes")
    private Integer availableTimeMinutes;

    @Schema(description = "Target muscle groups", example = "[\"chest\", \"shoulders\", \"triceps\"]")
    @JsonProperty("targetMuscleGroups")
    private List<String> targetMuscleGroups;

    @Schema(description = "Available equipment", example = "[\"barbell\", \"dumbbells\", \"bench\"]")
    @JsonProperty("availableEquipment")
    private List<String> availableEquipment;

    @Schema(description = "Difficulty preference", example = "INTERMEDIATE")
    private String difficulty;

    @Schema(description = "Whether to use previous workout data for recommendations")
    @JsonProperty("usePersonalHistory")
    private Boolean usePersonalHistory = true;
}
