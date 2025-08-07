package com.muscledia.workout_service.model.embedded;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An exercise performed during a workout with detailed set information")
public class WorkoutExercise {
    @Field("exercise_id")
    @NotBlank(message = "Exercise ID is required")
    @JsonProperty("exerciseId")
    @Schema(description = "Reference to the exercise definition", example = "507f1f77bcf86cd799439011")
    private String exerciseId;

    @NotBlank(message = "Exercise name is required")
    @JsonProperty("exerciseName")
    @Schema(description = "Name of the exercise", example = "Bench Press")
    private String exerciseName;

    @Field("exercise_order")
    @Min(value = 1, message = "Exercise order must be at least 1")
    @Max(value = 50, message = "Exercise order cannot exceed 50")
    @JsonProperty("exerciseOrder")
    @Schema(description = "Order of this exercise in the workout", example = "1")
    private Integer exerciseOrder;

    @NotBlank(message = "Exercise category is required")
    @JsonProperty("exerciseCategory")
    @Schema(description = "Category of the exercise", example = "STRENGTH", allowableValues = {"STRENGTH", "CARDIO", "FLEXIBILITY", "PLYOMETRIC"})
    private String exerciseCategory;

    @Field("primary_muscle_group")
    @JsonProperty("primaryMuscleGroup")
    @Schema(description = "Primary muscle group targeted", example = "chest")
    private String primaryMuscleGroup;

    @Field("secondary_muscle_groups")
    @JsonProperty("secondaryMuscleGroups")
    @Builder.Default
    @Schema(description = "Secondary muscle groups involved")
    private List<String> secondaryMuscleGroups = new ArrayList<>();

    @Schema(description = "Equipment used for this exercise", example = "barbell")
    private String equipment;

    // CORE PERFORMANCE DATA
    @NotEmpty(message = "At least one set is required for a completed exercise")
    @Size(max = 100, message = "Cannot exceed 100 sets per exercise")
    @Valid
    @Builder.Default
    @Schema(description = "Detailed set-by-set performance data")
    private List<WorkoutSet> sets = new ArrayList<>();

    // TIMING
    @Field("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("startedAt")
    @Schema(description = "When this exercise was started")
    private LocalDateTime startedAt;

    @Field("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("completedAt")
    @Schema(description = "When this exercise was completed")
    private LocalDateTime completedAt;

    // ADDITIONAL CONTEXT
    @Size(max = 500, message = "Exercise notes cannot exceed 500 characters")
    @Schema(description = "Notes specific to this exercise performance")
    private String notes;

    @Min(value = 1, message = "RPE must be at least 1")
    @Max(value = 10, message = "RPE cannot exceed 10")
    @Schema(description = "Overall Rate of Perceived Exertion for this exercise (1-10)")
    private Integer overallRpe;

    // BUSINESS METHODS

    /**
     * Add a set to this exercise
     */
    public void addSet(WorkoutSet set) {
        if (this.sets == null) {
            this.sets = new ArrayList<>();
        }
        set.setSetNumber(this.sets.size() + 1);
        this.sets.add(set);
    }

    /**
     * Start the exercise
     */
    public void startExercise() {
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Complete the exercise
     */
    public void completeExercise() {
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Calculate total volume for this exercise (sum of all sets)
     */
    public BigDecimal getTotalVolume() {
        if (sets == null || sets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return sets.stream()
                .map(WorkoutSet::getVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total reps for this exercise
     */
    public int getTotalReps() {
        if (sets == null || sets.isEmpty()) {
            return 0;
        }

        return sets.stream()
                .filter(set -> set.getReps() != null)
                .mapToInt(WorkoutSet::getReps)
                .sum();
    }

    /**
     * Get the maximum weight used in any set of this exercise
     */
    public BigDecimal getMaxWeight() {
        if (sets == null || sets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return sets.stream()
                .map(WorkoutSet::getWeightKg)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Get the total time spent on this exercise (including rest)
     */
    public Integer getTotalTimeSeconds() {
        if (sets == null || sets.isEmpty()) {
            return 0;
        }

        // Sum of all set durations plus rest times
        int setTime = sets.stream()
                .filter(set -> set.getDurationSeconds() != null)
                .mapToInt(WorkoutSet::getDurationSeconds)
                .sum();

        int restTime = sets.stream()
                .filter(set -> set.getRestSeconds() != null)
                .mapToInt(WorkoutSet::getRestSeconds)
                .sum();

        return setTime + restTime;
    }

    /**
     * Check if this exercise involves strength training (has weight/reps)
     */
    public boolean isStrengthExercise() {
        return sets != null && sets.stream()
                .anyMatch(set -> set.getWeightKg() != null && set.getReps() != null);
    }

    /**
     * Check if this exercise involves cardio (has duration/distance)
     */
    public boolean isCardioExercise() {
        return sets != null && sets.stream()
                .anyMatch(set -> set.getDurationSeconds() != null || set.getDistanceMeters() != null);
    }

    /**
     * Get average RPE across all sets (excluding null values)
     */
    public Double getAverageRpe() {
        if (sets == null || sets.isEmpty()) {
            return null;
        }

        return sets.stream()
                .filter(set -> set.getRpe() != null)
                .mapToInt(WorkoutSet::getRpe)
                .average()
                .orElse(0.0);
    }

    /**
     * Get the number of completed sets (non-failed sets)
     */
    public int getCompletedSetsCount() {
        if (sets == null || sets.isEmpty()) {
            return 0;
        }

        return (int) sets.stream()
                .filter(set -> Boolean.TRUE.equals(set.getCompleted()) &&
                        !Boolean.TRUE.equals(set.getFailure()))
                .count();
    }

    /**
     * Check if all planned sets were completed successfully
     */
    public boolean isFullyCompleted() {
        return sets != null && !sets.isEmpty() &&
                sets.stream().allMatch(set -> Boolean.TRUE.equals(set.getCompleted()));
    }


    /**
     * Get the number of sets performed
     */
    public int getSetCount() {
        return sets != null ? sets.size() : 0;
    }

    /**
     * Get the average weight across all sets
     */
    public BigDecimal getAverageWeight() {
        if (sets == null || sets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalWeight = sets.stream()
                .map(WorkoutSet::getWeightKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long weightedSets = sets.stream()
                .map(WorkoutSet::getWeightKg)
                .filter(Objects::nonNull)
                .count();

        return weightedSets > 0 ?
                totalWeight.divide(BigDecimal.valueOf(weightedSets), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    /**
     * Get estimated 1RM for this exercise
     */
    public BigDecimal getEstimated1RM() {
        if (sets == null || sets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return sets.stream()
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .map(set -> {
                    BigDecimal weight = set.getWeightKg();
                    int reps = set.getReps();

                    if (reps == 1) return weight;

                    // Epley formula: weight * (1 + reps/30)
                    BigDecimal multiplier = BigDecimal.ONE.add(
                            BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
                    return weight.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                })
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
}