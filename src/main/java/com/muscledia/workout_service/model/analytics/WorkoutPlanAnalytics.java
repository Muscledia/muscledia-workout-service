package com.muscledia.workout_service.model.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class WorkoutPlanAnalytics {
    private String planId;
    private String planTitle;
    private Long totalStarts;
    private Double averageCompletionRate;
    private Integer averageActualDuration;
    private Integer popularityRank;
    private LocalDateTime lastUsed;
    private Integer uniqueUsers;
}
