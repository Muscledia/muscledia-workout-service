package com.muscledia.workout_service.dto.response.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Personal record achievement data")
public class PersonalRecordResponse {

    @Schema(description = "Personal record ID", example = "507f1f77bcf86cd799439011")
    private String id;

    @Schema(description = "Exercise ID", example = "507f1f77bcf86cd799439011")
    private String exerciseId;

    @Schema(description = "Exercise name", example = "Deadlift")
    private String exerciseName;

    @Schema(description = "Type of record", example = "MAX_WEIGHT")
    private String recordType;

    @Schema(description = "Record value", example = "315.00")
    private BigDecimal value;

    @Schema(description = "Weight used for the record", example = "315.00")
    private BigDecimal weight;

    @Schema(description = "Repetitions performed", example = "1")
    private Integer reps;

    @Schema(description = "Sets performed", example = "1")
    private Integer sets;

    @Schema(description = "Date when record was achieved", example = "2024-01-15T10:30:00")
    private LocalDateTime achievedDate;

    @Schema(description = "Previous record value", example = "295.00")
    private BigDecimal previousRecord;

    @Schema(description = "Improvement percentage over previous record", example = "6.78")
    private Double improvementPercentage;

    @Schema(description = "Notes about the record achievement", example = "Perfect form, felt strong!")
    private String notes;

    @Schema(description = "Number of days since last PR", example = "42")
    private Long daysSinceLastPR;

    @Schema(description = "Whether this is a recent achievement (within last 30 days)", example = "true")
    private Boolean isRecentAchievement;
}