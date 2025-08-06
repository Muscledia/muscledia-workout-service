package com.muscledia.workout_service.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    private String workoutPlanId; // Reference to the plan used (optional)

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
    @Schema(description = "Type of workout", example = "STRENGTH", allowableValues = {"STRENGTH", "CARDIO", "FLEXIBILITY", "SPORTS", "MIXED"})
    private String workoutType;

    @Field("status")
    @NotNull(message = "Workout status is required")
    @Builder.Default
    @Schema(description = "Current status of the workout", allowableValues = {"PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    private WorkoutStatus status = WorkoutStatus.PLANNED;

    // TIMING FIELDS
    @Field("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("startedAt")
    @Schema(description = "When the workout was actually started")
    private LocalDateTime startedAt;

    @Field("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("completedAt")
    @Schema(description = "When the workout was completed")
    private LocalDateTime completedAt;

    @Field("duration_minutes")
    @Min(value = 0, message = "Duration cannot be negative")
    @Max(value = 600, message = "Duration cannot exceed 600 minutes")
    @JsonProperty("durationMinutes")
    @Schema(description = "Total duration of the workout in minutes")
    private Integer durationMinutes;

    // PERFORMANCE METRICS - These should be calculated from sets, not stored separately
    @Field("total_volume")
    @DecimalMin(value = "0.0", message = "Total volume cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Total volume must have at most 10 integer digits and 2 decimal places")
    @JsonProperty("totalVolume")
    @Schema(description = "Total volume (weight × reps) for the entire workout")
    private BigDecimal totalVolume;

    @Field("total_sets")
    @Min(value = 0, message = "Total sets cannot be negative")
    @JsonProperty("totalSets")
    @Schema(description = "Total number of sets performed")
    private Integer totalSets;

    @Field("total_reps")
    @Min(value = 0, message = "Total reps cannot be negative")
    @JsonProperty("totalReps")
    @Schema(description = "Total number of repetitions performed")
    private Integer totalReps;

    @Field("calories_burned")
    @Min(value = 0, message = "Calories burned cannot be negative")
    @JsonProperty("caloriesBurned")
    @Schema(description = "Estimated calories burned during workout")
    private Integer caloriesBurned;

    // EXERCISES - This is the core of the workout
    @NotEmpty(message = "At least one exercise is required for a completed workout")
    @Size(max = 50, message = "Cannot have more than 50 exercises in a single workout")
    @Valid
    @Builder.Default
    @Schema(description = "List of exercises performed in this workout")
    private List<WorkoutExercise> exercises = new ArrayList<>();

    // ADDITIONAL CONTEXT
    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    @Schema(description = "Additional notes about the workout")
    private String notes;

    @Schema(description = "Location where the workout was performed", example = "Home Gym")
    private String location;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 10, message = "Rating cannot exceed 10")
    @Schema(description = "User's subjective rating of the workout (1-10)")
    private Integer rating;

    @Schema(description = "Tags associated with this workout for categorization")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Field("metadata")
    @Schema(description = "Additional flexible metadata")
    private Map<String, Object> metadata;

    // AUDIT FIELDS
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
    @Schema(description = "Version for optimistic locking")
    private Long version;

    // BUSINESS METHODS

    /**
     * Start the workout session
     */
    public void startWorkout() {
        this.startedAt = LocalDateTime.now();
        this.status = WorkoutStatus.IN_PROGRESS;
        if (this.workoutDate == null) {
            this.workoutDate = this.startedAt;
        }
    }

    /**
     * Complete the workout session and calculate metrics
     */
    public void completeWorkout() {
        this.completedAt = LocalDateTime.now();
        this.status = WorkoutStatus.COMPLETED;

        // Calculate duration if not already set
        if (this.durationMinutes == null && this.startedAt != null) {
            this.durationMinutes = (int) Duration.between(startedAt, completedAt).toMinutes();
        }

        // Recalculate metrics from actual exercise data
        recalculateMetrics();
    }

    /**
     * Cancel the workout
     */
    public void cancelWorkout() {
        this.status = WorkoutStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Add an exercise to the workout
     */
    public void addExercise(WorkoutExercise exercise) {
        if (this.exercises == null) {
            this.exercises = new ArrayList<>();
        }
        exercise.setExerciseOrder(this.exercises.size() + 1);
        this.exercises.add(exercise);
        recalculateMetrics();
    }

    /**
     * Recalculate all metrics from the actual exercise data
     */
    public void recalculateMetrics() {
        if (exercises == null || exercises.isEmpty()) {
            this.totalVolume = BigDecimal.ZERO;
            this.totalSets = 0;
            this.totalReps = 0;
            return;
        }

        this.totalVolume = exercises.stream()
                .map(WorkoutExercise::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalSets = exercises.stream()
                .mapToInt(exercise -> exercise.getSets() != null ? exercise.getSets().size() : 0)
                .sum();

        this.totalReps = exercises.stream()
                .mapToInt(WorkoutExercise::getTotalReps)
                .sum();
    }

    /**
     * Get all unique muscle groups worked in this workout
     */
    public List<String> getWorkedMuscleGroups() {
        return exercises.stream()
                .flatMap(exercise -> {
                    List<String> muscleGroups = new ArrayList<>();
                    if (exercise.getPrimaryMuscleGroup() != null) {
                        muscleGroups.add(exercise.getPrimaryMuscleGroup());
                    }
                    if (exercise.getSecondaryMuscleGroups() != null) {
                        muscleGroups.addAll(exercise.getSecondaryMuscleGroups());
                    }
                    return muscleGroups.stream();
                })
                .distinct()
                .toList();
    }

    /**
     * Check if workout is currently active
     */
    public boolean isActive() {
        return status == WorkoutStatus.IN_PROGRESS;
    }

    /**
     * Check if workout is completed
     */
    public boolean isCompleted() {
        return status == WorkoutStatus.COMPLETED;
    }

    /**
     * Get actual workout duration in minutes
     */
    public Integer getActualDurationMinutes() {
        if (startedAt != null && completedAt != null) {
            return (int) Duration.between(startedAt, completedAt).toMinutes();
        }
        return durationMinutes;
    }


}