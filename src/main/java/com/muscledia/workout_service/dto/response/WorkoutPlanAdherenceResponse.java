package com.muscledia.workout_service.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Report showing how well a workout adhered to its original plan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Workout plan adherence analysis report")
public class WorkoutPlanAdherenceResponse {

    @Schema(description = "ID of the analyzed workout")
    @JsonProperty("workoutId")
    private String workoutId;

    @Schema(description = "ID of the original workout plan")
    @JsonProperty("workoutPlanId")
    private String workoutPlanId;

    @Schema(description = "Plan title for reference")
    @JsonProperty("planTitle")
    private String planTitle;

    @Schema(description = "Exercise adherence score (0.0 to 1.0)", example = "0.85")
    @JsonProperty("exerciseAdherence")
    private Double exerciseAdherence;

    @Schema(description = "Volume adherence score (0.0 to 1.0+)", example = "1.02")
    @JsonProperty("volumeAdherence")
    private Double volumeAdherence;

    @Schema(description = "Time adherence score (0.0 to 1.0)", example = "0.95")
    @JsonProperty("timeAdherence")
    private Double timeAdherence;

    @Schema(description = "Overall adherence score (0.0 to 1.0)", example = "0.89")
    @JsonProperty("overallAdherence")
    private Double overallAdherence;

    @Schema(description = "List of modifications made to the plan")
    private List<String> modifications;

    @Schema(description = "Performance comparison with plan targets")
    @JsonProperty("performanceAnalysis")
    private PerformanceAnalysis performanceAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceAnalysis {
        @Schema(description = "Number of exercises planned vs completed")
        @JsonProperty("exercisesPlanned")
        private Integer exercisesPlanned;

        @JsonProperty("exercisesCompleted")
        private Integer exercisesCompleted;

        @Schema(description = "Total sets planned vs completed")
        @JsonProperty("setsPlanned")
        private Integer setsPlanned;

        @JsonProperty("setsCompleted")
        private Integer setsCompleted;

        @Schema(description = "Planned vs actual workout duration in minutes")
        @JsonProperty("plannedDurationMinutes")
        private Integer plannedDurationMinutes;

        @JsonProperty("actualDurationMinutes")
        private Integer actualDurationMinutes;

        @Schema(description = "Exercises that were skipped")
        @JsonProperty("skippedExercises")
        private List<String> skippedExercises;

        @Schema(description = "Exercises that were added (not in plan)")
        @JsonProperty("addedExercises")
        private List<String> addedExercises;
    }
}
