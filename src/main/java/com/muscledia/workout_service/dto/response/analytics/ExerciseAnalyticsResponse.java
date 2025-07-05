package com.muscledia.workout_service.dto.response.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "Analytics data for a specific exercise")
public class ExerciseAnalyticsResponse {

    @Schema(description = "Exercise ID", example = "507f1f77bcf86cd799439011")
    private String exerciseId;

    @Schema(description = "Exercise name", example = "Bench Press")
    private String exerciseName;

    // Volume Metrics
    @Schema(description = "Total volume for this exercise", example = "8250.75")
    private BigDecimal totalVolume;

    @Schema(description = "Average volume per session", example = "275.02")
    private BigDecimal averageVolumePerSession;

    @Schema(description = "Volume trend direction", example = "INCREASING")
    private String volumeTrend;

    @Schema(description = "Volume change percentage", example = "12.5")
    private Double volumeChangePercentage;

    // Strength Metrics
    @Schema(description = "Maximum weight lifted", example = "225.00")
    private BigDecimal maxWeight;

    @Schema(description = "Average weight used", example = "185.50")
    private BigDecimal averageWeight;

    @Schema(description = "Estimated 1 rep max", example = "245.75")
    private BigDecimal estimated1RM;

    @Schema(description = "Strength trend direction", example = "INCREASING")
    private String strengthTrend;

    // Performance Metrics
    @Schema(description = "Total sets performed", example = "90")
    private Integer totalSets;

    @Schema(description = "Total reps performed", example = "720")
    private Integer totalReps;

    @Schema(description = "Average sets per session", example = "3.0")
    private Double averageSetsPerSession;

    @Schema(description = "Average reps per set", example = "8.0")
    private Double averageRepsPerSet;

    @Schema(description = "Exercise frequency per week", example = "1.5")
    private Double frequencyPerWeek;

    // Progress Tracking
    @Schema(description = "First time this exercise was recorded", example = "2024-01-01T10:00:00")
    private LocalDateTime firstRecorded;

    @Schema(description = "Last time this exercise was recorded", example = "2024-01-30T14:30:00")
    private LocalDateTime lastRecorded;

    @Schema(description = "Number of workout sessions including this exercise", example = "30")
    private Integer sessionsCount;

    @Schema(description = "Current personal record for this exercise")
    private PersonalRecordResponse currentPR;

    @Schema(description = "Progress score (0-100) based on improvement", example = "85.2")
    private Double progressScore;
}