package com.muscledia.workout_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscledia.workout_service.model.enums.SetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Set response within exercise
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Individual set performance data")
public class WorkoutSetResponse {

    @JsonProperty("setNumber")
    private Integer setNumber;

    @JsonProperty("weightKg")
    private BigDecimal weightKg;

    private Integer reps;

    @JsonProperty("durationSeconds")
    private Integer durationSeconds;

    @JsonProperty("distanceMeters")
    private BigDecimal distanceMeters;

    @JsonProperty("restSeconds")
    private Integer restSeconds;

    private Integer rpe;

    private Boolean completed;

    @JsonProperty("setType")
    private SetType setType;

    private String notes;

    @JsonProperty("startedAt")
    private String startedAt;

    @JsonProperty("completedAt")
    private String completedAt;

    private BigDecimal volume;
}
