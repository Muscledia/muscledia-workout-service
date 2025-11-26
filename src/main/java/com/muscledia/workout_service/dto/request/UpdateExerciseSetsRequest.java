package com.muscledia.workout_service.dto.request;

import com.muscledia.workout_service.model.embedded.PlannedExercise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateExerciseSetsRequest {
    @NotEmpty(message = "At least one set is required")
    @Valid
    private List<PlannedExercise.PlannedSet> sets;
}
