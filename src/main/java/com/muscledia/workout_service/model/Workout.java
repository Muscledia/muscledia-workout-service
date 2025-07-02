package com.muscledia.workout_service.model;

import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @Field("workout_plan_id")
    private String workoutPlanId; // Reference to the plan used (optional)

    @Field("workout_date")
    @NotNull(message = "Workout date is required")
    @PastOrPresent(message = "Workout date cannot be in the future")
    private LocalDateTime workoutDate;

    @Field("duration_minutes")
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 600, message = "Duration cannot exceed 600 minutes")
    private Integer durationMinutes;

    @Field("total_volume")
    @DecimalMin(value = "0.0", message = "Total volume cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Total volume must have at most 10 integer digits and 2 decimal places")
    private BigDecimal totalVolume;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    @NotNull(message = "Exercises list is required")
    @NotEmpty(message = "At least one exercise is required")
    @Size(max = 50, message = "Cannot have more than 50 exercises in a single workout")
    @Valid
    private List<WorkoutExercise> exercises;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}