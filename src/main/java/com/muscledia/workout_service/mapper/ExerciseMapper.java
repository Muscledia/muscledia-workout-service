package com.muscledia.workout_service.mapper;


import com.muscledia.workout_service.dto.response.ExerciseSummaryResponse;
import com.muscledia.workout_service.model.Exercise;
import org.springframework.stereotype.Component;

/**
 * Mapper for Exercise entity transformations
 *
 * SOLID: Single Responsibility - handles only Exercise mapping
 * CLEAN: Separates data transformation from business logic
 */
@Component
public class ExerciseMapper {

    /**
     * Convert Exercise entity to summary response DTO
     *
     * Includes all fields needed for exercise browsing and selection
     */
    public ExerciseSummaryResponse toSummaryResponse(Exercise exercise) {
        if (exercise == null) {
            return null;
        }

        return ExerciseSummaryResponse.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .bodyPart(exercise.getBodyPart())
                .equipment(exercise.getEquipment())
                .targetMuscle(exercise.getTargetMuscle())
                .secondaryMuscles(exercise.getSecondaryMuscles())
                .difficulty(exercise.getDifficulty())
                .category(exercise.getCategory())
                .description(exercise.getDescription())
                .build();
    }
}
