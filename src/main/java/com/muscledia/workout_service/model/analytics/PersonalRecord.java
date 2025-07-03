package com.muscledia.workout_service.model.analytics;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Document(collection = "personal_records")
@CompoundIndex(def = "{'user_id': 1, 'exercise_id': 1, 'record_type': 1}", unique = true)
public class PersonalRecord {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private Long userId;

    @Field("exercise_id")
    private String exerciseId;

    @Field("exercise_name")
    private String exerciseName;

    @Field("record_type")
    private String recordType; // "MAX_WEIGHT", "MAX_VOLUME", "MAX_REPS", "ESTIMATED_1RM"

    @Field("value")
    private BigDecimal value;

    @Field("weight")
    private BigDecimal weight;

    @Field("reps")
    private Integer reps;

    @Field("sets")
    private Integer sets;

    @Field("workout_id")
    private String workoutId;

    @Field("achieved_date")
    private LocalDateTime achievedDate;

    @Field("previous_record")
    private BigDecimal previousRecord;

    @Field("improvement_percentage")
    private Double improvementPercentage;

    @Field("notes")
    private String notes;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}