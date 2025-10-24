package com.muscledia.workout_service.dto.response.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * PRStatistics - Personal Record statistics for analytics
 */
@Data
@Builder
@Schema(description = "Personal Record statistics")
public class PRStatistics {
    @Schema(description = "Total number of personal records")
    @JsonProperty("totalPRs")
    private Integer totalPRs;

    @Schema(description = "Number of PRs achieved this month")
    @JsonProperty("prsThisMonth")
    private Integer prsThisMonth;

    @Schema(description = "Number of PRs achieved this week")
    @JsonProperty("prsThisWeek")
    private Integer prsThisWeek;

    @Schema(description = "Most recent PR achievement date")
    @JsonProperty("lastPRDate")
    private LocalDateTime lastPRDate;

    @Schema(description = "Exercise with most PRs")
    @JsonProperty("topExercise")
    private String topExercise;

    @Schema(description = "Average improvement percentage across all PRs")
    @JsonProperty("averageImprovement")
    private Double averageImprovement;

    @Schema(description = "Breakdown by record type (MAX_WEIGHT, MAX_REPS, etc.)")
    @JsonProperty("prsByType")
    private Map<String, Integer> prsByType;

    @Schema(description = "Recent significant improvements (over 10%)")
    @JsonProperty("significantImprovements")
    private Integer significantImprovements;

    @Schema(description = "Highest single improvement percentage")
    @JsonProperty("bestImprovement")
    private Double bestImprovement;

    @Schema(description = "Total weight PR value")
    @JsonProperty("totalWeightPRs")
    private BigDecimal totalWeightPRs;

    @Schema(description = "Total volume PR value")
    @JsonProperty("totalVolumePRs")
    private BigDecimal totalVolumePRs;

    @Schema(description = "PR frequency (PRs per week average)")
    @JsonProperty("prFrequency")
    private Double prFrequency;
}
