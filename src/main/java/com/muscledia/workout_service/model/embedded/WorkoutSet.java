package com.muscledia.workout_service.model.embedded;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Detailed performance data for a single set")
public class WorkoutSet {

    @Schema(description = "Set number within the exercise", example = "1")
    @Field("set_number")
    @JsonProperty("setNumber")
    private Integer setNumber;

    // STRENGTH METRICS
    @Schema(description = "Weight used in kilograms", example = "50.0")
    @DecimalMin(value = "0.0", message = "Weight cannot be negative")
    @Field("weight_kg")
    @JsonProperty("weightKg")
    private BigDecimal weightKg;

    @Schema(description = "Number of repetitions completed", example = "10")
    @Min(value = 0, message = "Reps cannot be negative")
    private Integer reps;

    // CARDIO METRICS
    @Schema(description = "Duration of the set in seconds", example = "30")
    @Min(value = 0, message = "Duration cannot be negative")
    @Field("duration_seconds")
    @JsonProperty("durationSeconds")
    private Integer durationSeconds;

    @Schema(description = "Distance covered in meters", example = "1000.0")
    @DecimalMin(value = "0.0", message = "Distance cannot be negative")
    @Field("distance_meters")
    @JsonProperty("distanceMeters")
    private BigDecimal distanceMeters;

    // REST AND INTENSITY
    @Schema(description = "Rest time after this set in seconds", example = "90")
    @Min(value = 0, message = "Rest time cannot be negative")
    @Field("rest_seconds")
    @JsonProperty("restSeconds")
    private Integer restSeconds;

    @Schema(description = "Rate of Perceived Exertion (1-10 scale)", example = "7")
    @Min(value = 1, message = "RPE must be at least 1")
    @Max(value = 10, message = "RPE cannot exceed 10")
    private Integer rpe;

    // SET CHARACTERISTICS
    @Schema(description = "Whether this set was completed successfully", example = "true")
    @Builder.Default
    private Boolean completed = true;

    @Schema(description = "Whether this set was a failure (couldn't complete planned reps)", example = "false")
    @Builder.Default
    private Boolean failure = false;

    @Schema(description = "Whether this set was a drop set", example = "false")
    @Builder.Default
    @Field("drop_set")
    @JsonProperty("dropSet")
    private Boolean dropSet = false;

    @Schema(description = "Whether this set was a warm-up set", example = "false")
    @Builder.Default
    @Field("warm_up")
    @JsonProperty("warmUp")
    private Boolean warmUp = false;

    @Schema(description = "Set type", example = "WORKING", allowableValues = {"WARM_UP", "WORKING", "DROP", "CLUSTER", "REST_PAUSE"})
    @Builder.Default
    @Field("set_type")
    @JsonProperty("setType")
    private String setType = "WORKING";

    // TIMING
    @Schema(description = "When the set was started")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field("started_at")
    @JsonProperty("startedAt")
    private LocalDateTime startedAt;

    @Schema(description = "When the set was completed")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field("completed_at")
    @JsonProperty("completedAt")
    private LocalDateTime completedAt;

    // ADDITIONAL CONTEXT
    @Schema(description = "Additional notes for this set", example = "Felt heavy today")
    @Size(max = 200, message = "Set notes cannot exceed 200 characters")
    private String notes;

    // BUSINESS METHODS

    /**
     * Calculate volume for this set (weight × reps)
     */
    public BigDecimal getVolume() {
        if (weightKg != null && reps != null && reps > 0) {
            return weightKg.multiply(BigDecimal.valueOf(reps));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Mark set as started
     */
    public void markStarted() {
        this.startedAt = LocalDateTime.now();
    }

    /**
     * Mark set as completed
     */
    public void markCompleted() {
        this.completed = true;
        this.completedAt = LocalDateTime.now();

        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    /**
     * Check if this is a strength-based set
     */
    public boolean isStrengthSet() {
        return weightKg != null && reps != null && reps > 0;
    }

    /**
     * Check if this is a cardio-based set
     */
    public boolean isCardioSet() {
        return durationSeconds != null || distanceMeters != null;
    }

    /**
     * Validate that the set has required data
     */
    public boolean isValid() {
        return isStrengthSet() || isCardioSet() ||
                (reps != null && reps > 0); // Bodyweight
    }
}
