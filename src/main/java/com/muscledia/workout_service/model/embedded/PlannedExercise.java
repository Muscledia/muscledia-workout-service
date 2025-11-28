package com.muscledia.workout_service.model.embedded;

import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
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


    // ==================== DENORMALIZED EXERCISE PROPERTIES ====================
    // These are snapshots from Exercise entity for display purposes
    // Nullable because they're optional display data

    /**
     * Body part targeted (e.g., "waist", "back", "chest")
     * Nullable - used for workout summary and muscle group tracking
     */
    private String bodyPart;

    /**
     * Equipment required (e.g., "barbell", "body weight")
     * Nullable - used for equipment preparation
     */
    private String equipment;

    /**
     * Primary muscle targeted (e.g., "abs", "biceps")
     * Nullable - used for muscle group tracking
     */
    private String targetMuscle;

    /**
     * Secondary muscles worked
     * Nullable - used for comprehensive muscle tracking
     */
    private List<String> secondaryMuscles;

    /**
     * Exercise difficulty level
     * Nullable - used for workout difficulty assessment
     */
    private ExerciseDifficulty difficulty;

    /**
     * Exercise category (e.g., STRENGTH, CARDIO)
     * Nullable - used for workout type classification
     */
    private ExerciseCategory category;

    /**
     * Exercise description
     * Nullable - useful for quick reference during workout
     */
    private String description;

    /**
     * Step-by-step instructions
     * Nullable - displayed during workout execution
     */
    private List<String> instructions;

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