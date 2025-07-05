package com.muscledia.workout_service.dto.response.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Progress tracking data with trend analysis")
public class ProgressTrackingResponse {

    @Schema(description = "Exercise ID", example = "507f1f77bcf86cd799439011")
    private String exerciseId;

    @Schema(description = "Exercise name", example = "Squat")
    private String exerciseName;

    @Schema(description = "Progress tracking period", example = "90")
    private Integer trackingPeriodDays;

    // Current Status
    @Schema(description = "Current maximum weight", example = "275.00")
    private BigDecimal currentMaxWeight;

    @Schema(description = "Current estimated 1RM", example = "295.50")
    private BigDecimal currentEstimated1RM;

    @Schema(description = "Current average volume per session", example = "2450.75")
    private BigDecimal currentAverageVolume;

    // Trend Analysis
    @Schema(description = "Weight progression trend (7 days)", example = "INCREASING")
    private String weightTrend7Days;

    @Schema(description = "Weight progression trend (30 days)", example = "INCREASING")
    private String weightTrend30Days;

    @Schema(description = "Volume progression trend (7 days)", example = "STABLE")
    private String volumeTrend7Days;

    @Schema(description = "Volume progression trend (30 days)", example = "INCREASING")
    private String volumeTrend30Days;

    // Progress Metrics
    @Schema(description = "Weight improvement over tracking period (%)", example = "18.5")
    private Double weightImprovementPercentage;

    @Schema(description = "Volume improvement over tracking period (%)", example = "22.3")
    private Double volumeImprovementPercentage;

    @Schema(description = "1RM improvement over tracking period (%)", example = "15.7")
    private Double oneRMImprovementPercentage;

    // Historical Data Points
    @Schema(description = "Historical progress data points for charting")
    private List<ProgressDataPoint> progressHistory;

    // Predictions
    @Schema(description = "Predicted 1RM in 30 days based on current trend", example = "305.25")
    private BigDecimal predicted1RMIn30Days;

    @Schema(description = "Predicted volume capacity in 30 days", example = "2650.00")
    private BigDecimal predictedVolumeIn30Days;

    @Schema(description = "Confidence level of predictions (0-100)", example = "78.5")
    private Double predictionConfidence;

    @Data
    @Schema(description = "Single data point in progress history")
    public static class ProgressDataPoint {
        @Schema(description = "Date of the data point", example = "2024-01-15")
        private LocalDate date;

        @Schema(description = "Maximum weight on this date", example = "265.00")
        private BigDecimal maxWeight;

        @Schema(description = "Average volume on this date", example = "2350.50")
        private BigDecimal averageVolume;

        @Schema(description = "Estimated 1RM on this date", example = "285.75")
        private BigDecimal estimated1RM;

        @Schema(description = "Workout frequency in past 7 days", example = "2")
        private Integer workoutFrequency7Days;
    }
}