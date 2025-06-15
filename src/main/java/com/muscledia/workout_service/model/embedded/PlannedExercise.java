package com.muscledia.workout_service.model.embedded;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

@Data
public class PlannedExercise {
    private Integer index;

    private String title;

    private String notes;

    @Field("exercise_template_id")
    private String exerciseTemplateId;

    @Field("superset_id")
    private String supersetId;

    @Field("rest_seconds")
    private Integer restSeconds;

    private List<PlannedSet> sets;

    @Data
    public static class PlannedSet {
        private Integer index;

        private String type; // "normal", "warmup", etc.

        @Field("weight_kg")
        private Double weightKg;

        private Integer reps;

        @Field("distance_meters")
        private Double distanceMeters;

        @Field("duration_seconds")
        private Integer durationSeconds;

        @Field("custom_metric")
        private String customMetric;
    }
}