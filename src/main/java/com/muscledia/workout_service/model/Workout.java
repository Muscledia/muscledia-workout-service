package com.muscledia.workout_service.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workouts")
@Schema(description = "A completed or in-progress workout session with detailed exercise performance")
public class Workout {
    // === CORE IDENTIFICATION ===
    @Id
    private String id;

    @Indexed
    @Field("user_id")
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    @JsonProperty("userId")
    private Long userId;

    @Field("workout_plan_id")
    @JsonProperty("workoutPlanId")
    private String workoutPlanId;

    // === BASIC INFORMATION ===
    @Field("workout_name")
    @JsonProperty("workoutName")
    private String workoutName;

    @Field("workout_date")
    @NotNull(message = "Workout date is required")
    @PastOrPresent(message = "Workout date cannot be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("workoutDate")
    private LocalDateTime workoutDate;

    @Field("workout_type")
    @NotBlank(message = "Workout type is required")
    @JsonProperty("workoutType")
    private String workoutType;

    @Field("status")
    @NotNull(message = "Workout status is required")
    @Builder.Default
    private WorkoutStatus status = WorkoutStatus.PLANNED;

    // === TIMING ===
    @Field("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("startedAt")
    private LocalDateTime startedAt;

    @Field("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("completedAt")
    private LocalDateTime completedAt;

    @Field("duration_minutes")
    @Min(value = 0, message = "Duration cannot be negative")
    @Max(value = 600, message = "Duration cannot exceed 600 minutes")
    @JsonProperty("durationMinutes")
    private Integer durationMinutes;

    @Field("calories_burned")
    @Min(value = 0, message = "Calories burned cannot be negative")
    @Max(value = 5000, message = "Calories burned cannot exceed 5000")
    @JsonProperty("caloriesBurned")
    private Integer caloriesBurned;

    /**
     * -- SETTER --
     *  Set total volume
     */
    @Setter
    @Field("total_volume")
    @Min(value = 0, message = "Total volume cannot be negative")
    @JsonProperty("totalVolume")
    @Schema(description = "Total volume (weight × reps) for the entire workout")
    private BigDecimal totalVolume;

    // === EXERCISES (CORE DATA) ===
    @NotEmpty(message = "At least one exercise is required for a completed workout")
    @Size(max = 50, message = "Cannot have more than 50 exercises in a single workout")
    @Valid
    @Builder.Default
    private List<WorkoutExercise> exercises = new ArrayList<>();

    // === CONTEXT ===
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;

    private String location;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 10, message = "Rating cannot exceed 10")
    private Integer rating;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Field("metadata")
    private Map<String, Object> metadata;

    // === AUDIT FIELDS ===
    @CreatedDate
    @Field("created_at")
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /**
     * Start the workout - simple state change only
     */
    public void startWorkout() {
        this.startedAt = LocalDateTime.now();
        this.status = WorkoutStatus.IN_PROGRESS;
        if (this.workoutDate == null) {
            this.workoutDate = this.startedAt;
        }
    }

    /**
     * Complete the workout - simple state change only
     * Calculations are done by WorkoutCalculationService
     */
    public void completeWorkout() {
        this.completedAt = LocalDateTime.now();
        this.status = WorkoutStatus.COMPLETED;
    }

    /**
     * Cancel the workout - simple state change only
     */
    public void cancelWorkout() {
        this.status = WorkoutStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Add an exercise - simple list operation
     */
    public void addExercise(WorkoutExercise exercise) {
        if (this.exercises == null) {
            this.exercises = new ArrayList<>();
        }
        exercise.setExerciseOrder(this.exercises.size() + 1);
        this.exercises.add(exercise);
    }

    /**
     * Simple state checks - no calculations
     */
    public boolean isActive() {
        return status == WorkoutStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return status == WorkoutStatus.COMPLETED;
    }

    /**
     * Get total volume - calculated from exercises or stored value
     */
    public BigDecimal getTotalVolume() {
        if (totalVolume != null) {
            return totalVolume;
        }

        // Calculate on-the-fly if not stored
        return calculateTotalVolumeFromExercises();
    }

    /**
     * Calculate total volume from all exercises
     */
    private BigDecimal calculateTotalVolumeFromExercises() {
        if (exercises == null || exercises.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return exercises.stream()
                .map(WorkoutExercise::getTotalVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total sets across all exercises
     */
    public int getTotalSets() {
        if (exercises == null) {
            return 0;
        }
        return exercises.stream()
                .mapToInt(WorkoutExercise::getTotalSets)
                .sum();
    }

    /**
     * Get total reps across all exercises
     */
    public int getTotalReps() {
        if (exercises == null) {
            return 0;
        }
        return exercises.stream()
                .mapToInt(WorkoutExercise::getTotalReps)
                .sum();
    }

    public boolean isPlanned() {
        return status == WorkoutStatus.PLANNED;
    }

}