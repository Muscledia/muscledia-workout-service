package com.muscledia.workout_service.model.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
@Data
public class WorkoutPlanUsageStats {
    private String planId;
    private String planTitle;
    private Long totalUsageCount;
    private Boolean isPublic;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Integer estimatedDurationMinutes;
    private Integer exerciseCount;
}
