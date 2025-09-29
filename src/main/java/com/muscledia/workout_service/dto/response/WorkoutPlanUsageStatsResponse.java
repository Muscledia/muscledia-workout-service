package com.muscledia.workout_service.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Statistics about how a workout plan is being used
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Usage statistics for a workout plan")
public class WorkoutPlanUsageStatsResponse {

    @Schema(description = "Plan identifier")
    @JsonProperty("planId")
    private String planId;

    @Schema(description = "Plan title")
    @JsonProperty("planTitle")
    private String planTitle;

    @Schema(description = "Total number of times this plan has been used")
    @JsonProperty("totalUsageCount")
    private Long totalUsageCount;

    @Schema(description = "Number of unique users who have used this plan")
    @JsonProperty("uniqueUsers")
    private Integer uniqueUsers;

    @Schema(description = "Average completion rate (0.0 to 1.0)")
    @JsonProperty("averageCompletionRate")
    private Double averageCompletionRate;

    @Schema(description = "Average actual duration vs planned duration")
    @JsonProperty("averageActualDuration")
    private Integer averageActualDuration;

    @JsonProperty("plannedDuration")
    private Integer plannedDuration;

    @Schema(description = "When this plan was last used")
    @JsonProperty("lastUsedAt")
    private LocalDateTime lastUsedAt;

    @Schema(description = "User's personal usage count for this plan")
    @JsonProperty("userUsageCount")
    private Integer userUsageCount;

    @Schema(description = "User's average completion rate for this plan")
    @JsonProperty("userCompletionRate")
    private Double userCompletionRate;

    @Schema(description = "Popularity rank among all plans")
    @JsonProperty("popularityRank")
    private Integer popularityRank;

    @Schema(description = "Whether this is a public or personal plan")
    @JsonProperty("isPublic")
    private Boolean isPublic;
}
