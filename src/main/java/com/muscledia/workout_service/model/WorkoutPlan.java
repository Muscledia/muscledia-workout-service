package com.muscledia.workout_service.model;

import com.muscledia.workout_service.model.embedded.PlannedExercise;
import com.muscledia.workout_service.model.enums.WorkoutDifficulty;
import com.muscledia.workout_service.model.enums.WorkoutType;
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
@Document(collection = "workout_plans")
public class WorkoutPlan {
    @Id
    private String id;

    @Indexed
    private String name;

    private String description;

    @Field("difficulty_level")
    private WorkoutDifficulty difficulty;

    @Field("workout_type")
    private WorkoutType type;

    @Field("estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Field("target_muscle_groups")
    private List<String> targetMuscleGroups;

    @Field("required_equipment")
    private List<String> requiredEquipment;

    @Field("planned_exercises")
    private List<PlannedExercise> exercises;

    @Field("is_public")
    private Boolean isPublic = false;

    @Field("created_by")
    private Long createdBy; // User ID who created this plan

    @Field("usage_count")
    private Long usageCount = 0L;

    private String tags;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}