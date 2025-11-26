package com.muscledia.workout_service.dto.response;

import com.muscledia.workout_service.model.embedded.MuscleGroupRef;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExerciseSummaryResponse {
    private String id;
    private String name;
    private String equipment;
    private String targetMuscle;
    private ExerciseDifficulty difficulty;
    private String animationUrl;
    private List<MuscleGroupRef> muscleGroups;
}
