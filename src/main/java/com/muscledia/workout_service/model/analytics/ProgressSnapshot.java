package com.muscledia.workout_service.model.analytics;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "progress_snapshots")
@CompoundIndex(def = "{'user_id': 1, 'exercise_id': 1, 'snapshot_date': 1}")
public class ProgressSnapshot {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private Long userId;

    @Field("exercise_id")
    private String exerciseId;

    @Field("exercise_name")
    private String exerciseName;

    @Field("snapshot_date")
    private LocalDate snapshotDate;

    // Current metrics at this point in time
    @Field("current_max_weight")
    private BigDecimal currentMaxWeight;

    @Field("current_volume")
    private BigDecimal currentVolume;

    @Field("current_estimated_1rm")
    private BigDecimal currentEstimated1RM;

    @Field("average_weight_last_30_days")
    private BigDecimal averageWeightLast30Days;

    @Field("total_volume_last_30_days")
    private BigDecimal totalVolumeLast30Days;

    @Field("workout_frequency_last_30_days")
    private Integer workoutFrequencyLast30Days;

    // Trend indicators
    @Field("weight_trend_7_days")
    private String weightTrend7Days; // "UP", "DOWN", "STABLE"

    @Field("weight_trend_30_days")
    private String weightTrend30Days;

    @Field("volume_trend_7_days")
    private String volumeTrend7Days;

    @Field("volume_trend_30_days")
    private String volumeTrend30Days;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}