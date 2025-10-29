package com.muscledia.workout_service.model.analytics;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Document(collection = "workout_analytics")
public class WorkoutAnalytics {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private Long userId;

    @Field("analysis_period")
    private String analysisPeriod; // "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "ALL_TIME"

    @Field("period_start")
    private LocalDateTime periodStart;

    @Field("period_end")
    private LocalDateTime periodEnd;

    // Workout Statistics
    @Field("total_workouts")
    private Integer totalWorkouts;

    @Field("total_duration_minutes")
    private Integer totalDurationMinutes;

    @Field("average_duration_minutes")
    private Double averageDurationMinutes;

    @Field("total_volume")
    private BigDecimal totalVolume;

    @Field("average_volume_per_workout")
    private BigDecimal averageVolumePerWorkout;

    @Field("workout_frequency_per_week")
    private Double workoutFrequencyPerWeek;

    // Progress Metrics
    @Field("volume_trend")
    private String volumeTrend; // "INCREASING", "DECREASING", "STABLE"

    @Field("volume_change_percentage")
    private Double volumeChangePercentage;

    @Field("strength_progression_score")
    private Double strengthProgressionScore; // 0-100 score

    // Exercise-specific analytics
    @Field("exercise_analytics")
    private List<ExerciseAnalytics> exerciseAnalytics;

    // Personal Records
    @Field("new_prs_count")
    private Integer newPRsCount;

    @Field("recent_prs")
    private List<PersonalRecord> recentPRs;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;
}