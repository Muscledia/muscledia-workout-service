package com.muscledia.workout_service.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Summary information about a workout plan for selection/display purposes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Summary of a workout plan for selection")
public class WorkoutPlanSummaryResponse {

    @Schema(description = "Unique identifier for the workout plan")
    private String id;

    @Schema(description = "Title of the workout plan", example = "Upper/Lower Split - Intermediate")
    private String title;

    @Schema(description = "Brief description of the plan")
    private String description;

    @Schema(description = "Number of exercises in this plan", example = "6")
    @JsonProperty("exerciseCount")
    private Integer exerciseCount;

    @Schema(description = "Estimated duration in minutes", example = "75")
    @JsonProperty("estimatedDurationMinutes")
    private Integer estimatedDurationMinutes;

    @Schema(description = "Difficulty level", example = "INTERMEDIATE")
    private String difficulty;

    @Schema(description = "Primary workout type", example = "STRENGTH")
    @JsonProperty("workoutType")
    private String workoutType;

    @Schema(description = "Whether this plan is public or personal")
    @JsonProperty("isPublic")
    private Boolean isPublic;

    @Schema(description = "Number of times this plan has been used")
    @JsonProperty("usageCount")
    private Long usageCount;

    @Schema(description = "When this plan was created")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Schema(description = "Primary muscle groups targeted")
    @JsonProperty("targetMuscleGroups")
    private List<String> targetMuscleGroups;

    @Schema(description = "Equipment required for this plan")
    @JsonProperty("requiredEquipment")
    private List<String> requiredEquipment;

    @Schema(description = "Tags associated with this plan")
    private List<String> tags;

    @Schema(description = "Last time this plan was used by the current user")
    @JsonProperty("lastUsedAt")
    private LocalDateTime lastUsedAt;
}
