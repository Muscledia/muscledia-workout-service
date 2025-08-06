package com.muscledia.workout_service.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive workout response with all details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Complete workout session data with performance metrics")
public class WorkoutResponse {
    @Schema(description = "Unique workout identifier")
    private String id;

    @Schema(description = "User who performed the workout")
    @JsonProperty("userId")
    private Long userId;

    @Schema(description = "Workout name")
    @JsonProperty("workoutName")
    private String workoutName;

    @Schema(description = "Template used (if any)")
    @JsonProperty("workoutPlanId")
    private String workoutPlanId;

    @Schema(description = "Workout type")
    @JsonProperty("workoutType")
    private String workoutType;

    @Schema(description = "Current status")
    private String status;

    @Schema(description = "When workout started")
    @JsonProperty("startedAt")
    private String startedAt;

    @Schema(description = "When workout completed")
    @JsonProperty("completedAt")
    private String completedAt;

    @Schema(description = "Duration in minutes")
    @JsonProperty("durationMinutes")
    private Integer durationMinutes;

    @Schema(description = "Exercises performed")
    private List<WorkoutExerciseResponse> exercises;

    @Schema(description = "Performance metrics")
    private WorkoutMetrics metrics;

    @Schema(description = "Additional context")
    private WorkoutContext context;

    /**
     * Performance metrics summary
     */
    @Data
    @Builder
    @Schema(description = "Workout performance summary")
    public static class WorkoutMetrics {
        @JsonProperty("totalVolume")
        private BigDecimal totalVolume;

        @JsonProperty("totalSets")
        private Integer totalSets;

        @JsonProperty("totalReps")
        private Integer totalReps;

        @JsonProperty("caloriesBurned")
        private Integer caloriesBurned;

        @JsonProperty("workedMuscleGroups")
        private List<String> workedMuscleGroups;

        @JsonProperty("personalRecordsAchieved")
        private Integer personalRecordsAchieved;
    }

    /**
     * Additional workout context
     */
    @Data
    @Builder
    @Schema(description = "Additional workout context and metadata")
    public static class WorkoutContext {
        private String location;
        private String notes;
        private Integer rating;
        private List<String> tags;
    }
}
