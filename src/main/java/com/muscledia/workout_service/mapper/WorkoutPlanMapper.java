package com.muscledia.workout_service.mapper;

import com.muscledia.workout_service.constant.WorkoutPlanConstants;
import com.muscledia.workout_service.dto.request.AddExerciseToPlanRequest;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.embedded.PlannedExercise;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for WorkoutPlan related transformations
 */
@Component
public class WorkoutPlanMapper {

    public PlannedExercise toPlannedExercise(Exercise exercise, AddExerciseToPlanRequest request) {
        if (exercise == null) {
            return null;
        }

        PlannedExercise planned = new PlannedExercise();

        // Basic identification
        planned.setTitle(exercise.getName());
        planned.setExerciseTemplateId(exercise.getId());

        // Denormalized Exercise properties
        planned.setBodyPart(exercise.getBodyPart());
        planned.setEquipment(exercise.getEquipment());
        planned.setTargetMuscle(exercise.getTargetMuscle());
        planned.setSecondaryMuscles(exercise.getSecondaryMuscles());
        planned.setDifficulty(exercise.getDifficulty());
        planned.setCategory(exercise.getCategory());
        planned.setDescription(exercise.getDescription());
        planned.setInstructions(exercise.getInstructions());

        // User's plan-specific configuration
        // FIX: Ensure getters match DTO properties
        if (request != null) {
            planned.setNotes(request.getNotes());
            planned.setRestSeconds(getRestSeconds(request));
            planned.setSets(createPlannedSets(request));
        } else {
            // Fallback default sets if request is null (edge case)
            planned.setRestSeconds(WorkoutPlanConstants.DEFAULT_REST_SECONDS);
            planned.setSets(new ArrayList<>());
        }

        return planned;
    }

    private List<PlannedExercise.PlannedSet> createPlannedSets(AddExerciseToPlanRequest request) {
        int numberOfSets = getNumberOfSets(request);
        List<PlannedExercise.PlannedSet> sets = new ArrayList<>(numberOfSets);

        for (int i = 0; i < numberOfSets; i++) {
            sets.add(createPlannedSet(i, request));
        }

        return sets;
    }

    private PlannedExercise.PlannedSet createPlannedSet(int index, AddExerciseToPlanRequest request) {
        PlannedExercise.PlannedSet set = new PlannedExercise.PlannedSet();
        set.setIndex(index);
        set.setType("normal");

        // FIX: Ensure these methods exist in your DTO
        if (hasRepRange(request)) {
            set.setRepRangeStart(request.getRepRangeStart());
            set.setRepRangeEnd(request.getRepRangeEnd());
        } else {
            set.setReps(getTargetReps(request));
        }

        if (request.getTargetWeight() != null) {
            set.setWeightKg(request.getTargetWeight());
        }

        return set;
    }

    private Integer getRestSeconds(AddExerciseToPlanRequest request) {
        return request.getRestSeconds() != null
                ? request.getRestSeconds()
                : WorkoutPlanConstants.DEFAULT_REST_SECONDS;
    }

    private Integer getNumberOfSets(AddExerciseToPlanRequest request) {
        return request.getNumberOfSets() != null
                ? request.getNumberOfSets()
                : WorkoutPlanConstants.DEFAULT_NUMBER_OF_SETS;
    }

    private Integer getTargetReps(AddExerciseToPlanRequest request) {
        return request.getTargetReps() != null
                ? request.getTargetReps()
                : WorkoutPlanConstants.DEFAULT_REPS_PER_SET;
    }

    private boolean hasRepRange(AddExerciseToPlanRequest request) {
        return request.getRepRangeStart() != null && request.getRepRangeEnd() != null;
    }
}