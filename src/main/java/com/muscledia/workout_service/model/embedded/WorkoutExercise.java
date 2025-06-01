package com.muscledia.workout_service.model.embedded;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;
import java.math.BigDecimal;

@Data
public class WorkoutExercise {
    @Field("exercise_id")
    private String exerciseId;

    private Integer sets;

    private Integer reps;

    private BigDecimal weight;

    @Field("exercise_order")
    private Integer order;

    private String notes;
}