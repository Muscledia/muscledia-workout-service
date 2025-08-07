package com.muscledia.workout_service.dto.request.embedded;

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

import java.math.BigDecimal;

/**
 * Enhanced LogSetRequest with all necessary fields for comprehensive set logging
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to log detailed performance data for an exercise set")
public class LogSetRequest {
    // STRENGTH METRICS
    @Schema(description = "Weight used in kilograms", example = "50.0")
    @DecimalMin(value = "0.0", message = "Weight cannot be negative")
    @JsonProperty("weightKg")
    private BigDecimal weightKg;

    @Schema(description = "Number of repetitions completed", example = "10")
    @Min(value = 0, message = "Reps cannot be negative")
    private Integer reps;

    // CARDIO METRICS
    @Schema(description = "Duration of the set in seconds (for time-based exercises)", example = "30")
    @Min(value = 0, message = "Duration cannot be negative")
    @JsonProperty("durationSeconds")
    private Integer durationSeconds;

    @Schema(description = "Distance covered in meters (for cardio exercises)", example = "1000.0")
    @DecimalMin(value = "0.0", message = "Distance cannot be negative")
    @JsonProperty("distanceMeters")
    private BigDecimal distanceMeters;

    // REST AND INTENSITY
    @Schema(description = "Rest time after this set in seconds", example = "90")
    @Min(value = 0, message = "Rest time cannot be negative")
    @JsonProperty("restSeconds")
    private Integer restSeconds;

    @Schema(description = "Rate of Perceived Exertion (1-10 scale)", example = "7")
    @Min(value = 1, message = "RPE must be at least 1")
    @Max(value = 10, message = "RPE cannot exceed 10")
    private Integer rpe;

    // SET STATUS AND TYPE
    @Schema(description = "Whether this set was completed successfully", example = "true")
    @Builder.Default
    private Boolean completed = true;

    @Schema(description = "Whether this set was a failure (couldn't complete planned reps)", example = "false")
    @Builder.Default
    private Boolean failure = false;

    @Schema(description = "Whether this set was a drop set", example = "false")
    @Builder.Default
    @JsonProperty("dropSet")
    private Boolean dropSet = false;

    @Schema(description = "Whether this set was a warm-up set", example = "false")
    @Builder.Default
    @JsonProperty("warmUp")
    private Boolean warmUp = false;

    @Schema(description = "Type of set", example = "WORKING",
            allowableValues = {"WARM_UP", "WORKING", "DROP", "CLUSTER", "REST_PAUSE"})
    @Builder.Default
    @JsonProperty("setType")
    private String setType = "WORKING";

    // ADDITIONAL CONTEXT
    @Schema(description = "Additional notes for this set", example = "Felt heavy today")
    @Size(max = 200, message = "Set notes cannot exceed 200 characters")
    private String notes;

    /**
     * Validation method to ensure set has meaningful data
     */
    public boolean isValid() {
        return hasStrengthData() || hasCardioData() || hasBodyweightData();
    }

    public boolean hasStrengthData() {
        return weightKg != null && reps != null && reps > 0;
    }

    public boolean hasCardioData() {
        return durationSeconds != null || distanceMeters != null;
    }

    public boolean hasBodyweightData() {
        return reps != null && reps > 0 && (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) == 0);
    }
}
