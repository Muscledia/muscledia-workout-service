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
 * Exercise response within workout
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Exercise performance data within a workout")
public class WorkoutExerciseResponse {
    @JsonProperty("exerciseId")
    private String exerciseId;

    @JsonProperty("exerciseName")
    private String exerciseName;

    @JsonProperty("exerciseOrder")
    private Integer exerciseOrder;

    @JsonProperty("exerciseCategory")
    private String exerciseCategory;

    @JsonProperty("primaryMuscleGroup")
    private String primaryMuscleGroup;

    @JsonProperty("secondaryMuscleGroups")
    private List<String> secondaryMuscleGroups;

    private String equipment;

    private List<WorkoutSetResponse> sets;

    private String notes;

    @JsonProperty("startedAt")
    private String startedAt;

    @JsonProperty("completedAt")
    private String completedAt;

    // Performance summaries
    @JsonProperty("totalVolume")
    private BigDecimal totalVolume;

    @JsonProperty("totalReps")
    private Integer totalReps;

    @JsonProperty("maxWeight")
    private BigDecimal maxWeight;

    @JsonProperty("averageRpe")
    private Double averageRpe;

    @JsonProperty("completedSets")
    private Integer completedSets;
}
