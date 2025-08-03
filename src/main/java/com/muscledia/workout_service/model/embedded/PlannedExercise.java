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

        // FIXED: Add rep range fields to match Hevy API structure
        private Integer repRangeStart;
        private Integer repRangeEnd;

        /**
         * Helper method to check if this set has a rep range
         */
        public boolean hasRepRange() {
            return repRangeStart != null && repRangeEnd != null;
        }

        /**
         * Helper method to get rep range as string (e.g., "10-12")
         */
        public String getRepRangeString() {
            if (hasRepRange()) {
                return repRangeStart + "-" + repRangeEnd;
            }
            return null;
        }

        /**
         * Helper method to get effective reps (either reps or rep range)
         */
        public String getEffectiveReps() {
            if (reps != null) {
                return reps.toString();
            } else if (hasRepRange()) {
                return getRepRangeString();
            }
            return "N/A";
        }
    }
}