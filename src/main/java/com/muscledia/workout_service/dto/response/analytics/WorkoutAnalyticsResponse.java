package com.muscledia.workout_service.dto.response.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Comprehensive workout analytics and insights for a user")
public class WorkoutAnalyticsResponse {

    @Schema(description = "Analysis period", example = "MONTHLY")
    private String analysisPeriod;

    @Schema(description = "Period start date", example = "2024-01-01T00:00:00")
    private LocalDateTime periodStart;

    @Schema(description = "Period end date", example = "2024-01-31T23:59:59")
    private LocalDateTime periodEnd;

    // Workout Statistics
    @Schema(description = "Total number of workouts in period", example = "24")
    private Integer totalWorkouts;

    @Schema(description = "Total workout duration in minutes", example = "1440")
    private Integer totalDurationMinutes;

    @Schema(description = "Average workout duration in minutes", example = "60.0")
    private Double averageDurationMinutes;

    @Schema(description = "Total volume lifted", example = "45750.50")
    private BigDecimal totalVolume;

    @Schema(description = "Average volume per workout", example = "1906.27")
    private BigDecimal averageVolumePerWorkout;

    @Schema(description = "Workout frequency per week", example = "3.5")
    private Double workoutFrequencyPerWeek;

    // Progress Metrics
    @Schema(description = "Volume trend direction", example = "INCREASING")
    private String volumeTrend;

    @Schema(description = "Volume change percentage", example = "15.3")
    private Double volumeChangePercentage;

    @Schema(description = "Overall strength progression score (0-100)", example = "78.5")
    private Double strengthProgressionScore;

    // Personal Records
    @Schema(description = "Number of new PRs achieved in period", example = "5")
    private Integer newPRsCount;

    @Schema(description = "Recent personal records")
    private List<PersonalRecordResponse> recentPRs;

    // Exercise-specific analytics
    @Schema(description = "Analytics breakdown by exercise")
    private List<ExerciseAnalyticsResponse> exerciseAnalytics;

    // Insights and Recommendations
    @Schema(description = "AI-generated insights and recommendations")
    private List<String> insights;

    @Schema(description = "Recommended focus areas for improvement")
    private List<String> recommendations;
}