package com.muscledia.workout_service.model.analytics;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Document(collection = "personal_records")
@CompoundIndex(def = "{'user_id': 1, 'exercise_id': 1, 'record_type': 1}", unique = true)
public class PersonalRecord {

    @Id
    @Schema(description = "Unique record identifier")
    private String id;

    @Indexed
    @Field("user_id")
    @JsonProperty("userId")
    @Schema(description = "ID of the user who achieved this record", example = "12345")
    private Long userId;

    @Indexed
    @Field("exercise_id")
    @JsonProperty("exerciseId")
    @Schema(description = "ID of the exercise", example = "507f1f77bcf86cd799439011")
    private String exerciseId;

    @Field("exercise_name")
    @JsonProperty("exerciseName")
    @Schema(description = "Name of the exercise", example = "Bench Press")
    private String exerciseName;

    @Indexed
    @Field("record_type")
    @JsonProperty("recordType")
    @Schema(description = "Type of record", example = "MAX_WEIGHT",
            allowableValues = {"MAX_WEIGHT", "MAX_REPS", "MAX_VOLUME", "ESTIMATED_1RM"})
    private String recordType;

    @Field("value")
    @Schema(description = "The record value", example = "100.0")
    private BigDecimal value;

    @Field("weight_kg")
    @JsonProperty("weightKg")
    @Schema(description = "Weight used when achieving this record", example = "100.0")
    private BigDecimal weight;

    @Schema(description = "Reps performed when achieving this record", example = "5")
    private Integer reps;

    @Schema(description = "Number of sets when achieving this record", example = "3")
    private Integer sets;

    @Field("workout_id")
    @JsonProperty("workoutId")
    @Schema(description = "ID of the workout where this record was achieved")
    private String workoutId;

    @Field("unit")
    private String unit; // "kg", "lbs", "minutes", "reps"

    @Indexed
    @Field("achieved_date")
    @JsonProperty("achievedDate")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this record was achieved")
    private LocalDateTime achievedDate;

    @Field("previous_record")
    @JsonProperty("previousRecord")
    @Schema(description = "Previous record value that was beaten")
    private BigDecimal previousRecord;

    @Field("improvement_percentage")
    @JsonProperty("improvementPercentage")
    @Schema(description = "Percentage improvement over previous record", example = "15.5")
    private Double improvementPercentage;

    @Field("notes")
    @Schema(description = "Additional notes about this record")
    private String notes;

    @Field("verified")
    @Builder.Default
    @Schema(description = "Whether this record has been verified", example = "true")
    private Boolean verified = true;

    @CreatedDate
    @Field("created_at")
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Check if this is a weight-based record
     */
    public boolean isWeightRecord() {
        return "MAX_WEIGHT".equals(recordType) || "ESTIMATED_1RM".equals(recordType);
    }

    /**
     * Check if this is a volume-based record
     */
    public boolean isVolumeRecord() {
        return "MAX_VOLUME".equals(recordType);
    }

    /**
     * Check if this is a reps-based record
     */
    public boolean isRepsRecord() {
        return "MAX_REPS".equals(recordType);
    }

    /**
     * Get formatted record description
     */
    public String getFormattedDescription() {
        switch (recordType) {
            case "MAX_WEIGHT":
                return String.format("%.1f kg", value);
            case "MAX_REPS":
                return String.format("%d reps", value.intValue());
            case "MAX_VOLUME":
                return String.format("%.1f kg total", value);
            case "ESTIMATED_1RM":
                return String.format("%.1f kg (est.)", value);
            default:
                return value.toString();
        }
    }
}