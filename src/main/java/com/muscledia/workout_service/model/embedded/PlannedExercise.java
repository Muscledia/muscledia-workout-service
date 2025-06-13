package com.muscledia.workout_service.model.embedded;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class PlannedExercise {
    @Field("exercise_id")
    private String exerciseId;

    @Field("exercise_name")
    private String exerciseName; // Denormalized for easier display

    @Field("target_sets")
    private Integer targetSets;

    @Field("target_reps_min")
    private Integer targetRepsMin;

    @Field("target_reps_max")
    private Integer targetRepsMax;

    @Field("rest_seconds")
    private Integer restSeconds;

    @Field("exercise_order")
    private Integer order;

    @Field("is_superset")
    private Boolean isSuperset = false;

    @Field("superset_group")
    private String supersetGroup; // Group exercises in a superset

    private String notes;

    // Optional: intensity techniques
    @Field("rpe_target")
    private Integer rpeTarget; // Rate of Perceived Exertion (1-10)

    @Field("percentage_1rm")
    private Integer percentage1RM; // Percentage of 1 Rep Max
}