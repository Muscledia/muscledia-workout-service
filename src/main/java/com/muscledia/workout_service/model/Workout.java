package com.muscledia.workout_service.model;

import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "workouts")
public class Workout {
    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private Long userId;

    @Field("workout_plan_id")
    private String workoutPlanId; // Reference to the plan used (optional)

    @Field("workout_date")
    private LocalDateTime workoutDate;

    @Field("duration_minutes")
    private Integer durationMinutes;

    @Field("total_volume")
    private BigDecimal totalVolume;

    private String notes;

    private List<WorkoutExercise> exercises;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}