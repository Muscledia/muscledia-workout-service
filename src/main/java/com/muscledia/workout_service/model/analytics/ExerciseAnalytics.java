package com.muscledia.workout_service.model.analytics;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExerciseAnalytics {

    @Field("exercise_id")
    private String exerciseId;

    @Field("exercise_name")
    private String exerciseName;

    // Volume Metrics
    @Field("total_volume")
    private BigDecimal totalVolume;

    @Field("average_volume_per_session")
    private BigDecimal averageVolumePerSession;

    @Field("volume_trend")
    private String volumeTrend; // "INCREASING", "DECREASING", "STABLE"

    @Field("volume_change_percentage")
    private Double volumeChangePercentage;

    // Strength Metrics
    @Field("max_weight")
    private BigDecimal maxWeight;

    @Field("average_weight")
    private BigDecimal averageWeight;

    @Field("estimated_1rm")
    private BigDecimal estimated1RM;

    @Field("strength_trend")
    private String strengthTrend; // "INCREASING", "DECREASING", "STABLE"

    // Performance Metrics
    @Field("total_sets")
    private Integer totalSets;

    @Field("total_reps")
    private Integer totalReps;

    @Field("average_sets_per_session")
    private Double averageSetsPerSession;

    @Field("average_reps_per_set")
    private Double averageRepsPerSet;

    @Field("frequency_per_week")
    private Double frequencyPerWeek;

    // Progress Tracking
    @Field("first_recorded")
    private LocalDateTime firstRecorded;

    @Field("last_recorded")
    private LocalDateTime lastRecorded;

    @Field("sessions_count")
    private Integer sessionsCount;

    @Field("current_pr")
    private PersonalRecord currentPR;

    @Field("progress_score")
    private Double progressScore; // 0-100 score based on improvement
}