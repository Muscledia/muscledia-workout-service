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
 *
 * SOLID: Single Responsibility - handles only workout plan mapping
 * CLEAN: Separates data transformation from business logic
 */
@Component
public class WorkoutPlanMapper {

    /**
     * Convert Exercise from library to PlannedExercise for workout plan
     *
     * DENORMALIZATION STRATEGY:
     * Copies Exercise properties to PlannedExercise for:
     * - Faster workout plan display (no Exercise lookup needed)
     * - Showing exercise details during workout execution
     * - Workout summary with muscle groups and equipment
     *
     * Trade-off: Slight data duplication for better read performance
     *
     * @param exercise Exercise from library
     * @param request User's configuration for how to add this exercise
     * @return PlannedExercise ready to be added to workout plan
     */
    public PlannedExercise toPlannedExercise(Exercise exercise, AddExerciseToPlanRequest request) {
        if (exercise == null) {
            return null;
        }

        PlannedExercise planned = new PlannedExercise();

        // Basic identification
        planned.setTitle(exercise.getName());
        planned.setExerciseTemplateId(exercise.getId());

        // Denormalized Exercise properties (nullable - for display)
        planned.setBodyPart(exercise.getBodyPart());
        planned.setEquipment(exercise.getEquipment());
        planned.setTargetMuscle(exercise.getTargetMuscle());
        planned.setSecondaryMuscles(exercise.getSecondaryMuscles());
        planned.setDifficulty(exercise.getDifficulty());
        planned.setCategory(exercise.getCategory());
        planned.setDescription(exercise.getDescription());
        planned.setInstructions(exercise.getInstructions());

        // User's plan-specific configuration
        planned.setNotes(request.getNotes());
        planned.setRestSeconds(getRestSeconds(request));
        planned.setSets(createPlannedSets(request));

        return planned;
    }

    /**
     * Create planned sets based on user request or defaults
     */
    private List<PlannedExercise.PlannedSet> createPlannedSets(AddExerciseToPlanRequest request) {
        int numberOfSets = getNumberOfSets(request);
        List<PlannedExercise.PlannedSet> sets = new ArrayList<>(numberOfSets);

        for (int i = 0; i < numberOfSets; i++) {
            sets.add(createPlannedSet(i, request));
        }

        return sets;
    }

    /**
     * Create a single planned set
     */
    private PlannedExercise.PlannedSet createPlannedSet(int index, AddExerciseToPlanRequest request) {
        PlannedExercise.PlannedSet set = new PlannedExercise.PlannedSet();
        set.setIndex(index);
        set.setType("normal");

        // Configure reps - either as range or fixed value
        if (hasRepRange(request)) {
            set.setRepRangeStart(request.getRepRangeStart());
            set.setRepRangeEnd(request.getRepRangeEnd());
        } else {
            set.setReps(getTargetReps(request));
        }

        // Optional weight
        if (request.getTargetWeight() != null) {
            set.setWeightKg(request.getTargetWeight());
        }

        return set;
    }

    /**
     * Get rest seconds from request or use default
     */
    private Integer getRestSeconds(AddExerciseToPlanRequest request) {
        return request.getRestSeconds() != null
                ? request.getRestSeconds()
                : WorkoutPlanConstants.DEFAULT_REST_SECONDS;
    }

    /**
     * Get number of sets from request or use default
     */
    private Integer getNumberOfSets(AddExerciseToPlanRequest request) {
        return request.getNumberOfSets() != null
                ? request.getNumberOfSets()
                : WorkoutPlanConstants.DEFAULT_NUMBER_OF_SETS;
    }

    /**
     * Get target reps from request or use default
     */
    private Integer getTargetReps(AddExerciseToPlanRequest request) {
        return request.getTargetReps() != null
                ? request.getTargetReps()
                : WorkoutPlanConstants.DEFAULT_REPS_PER_SET;
    }

    /**
     * Check if request has a rep range configured
     */
    private boolean hasRepRange(AddExerciseToPlanRequest request) {
        return request.getRepRangeStart() != null && request.getRepRangeEnd() != null;
    }
}
