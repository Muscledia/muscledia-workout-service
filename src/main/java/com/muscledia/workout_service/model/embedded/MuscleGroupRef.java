package com.muscledia.workout_service.model.embedded;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class MuscleGroupRef {
    @Field("muscle_id")
    private String muscleId;

    private String name;

    @Field("is_primary")
    private boolean isPrimary;
}