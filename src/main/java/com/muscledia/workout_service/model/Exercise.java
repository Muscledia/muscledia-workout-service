package com.muscledia.workout_service.model;

import com.muscledia.workout_service.model.embedded.MuscleGroupRef;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "exercises")
public class Exercise {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("external_api_id")
    private String externalApiId;

    private String name;

    private String description;

    private String equipment;

    private ExerciseDifficulty difficulty;

    @Field("animation_url")
    private String animationUrl;

    @Field("target_muscle")
    private String targetMuscle;

    @Field("muscle_groups")
    private List<MuscleGroupRef> muscleGroups;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}