package com.muscledia.workout_service.external.hevy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HevyWorkoutRoutine {
    private String id;
    private String title;

    @JsonProperty("folder_id")
    private Long folderId;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private List<HevyExercise> exercises;

    @Data
    public static class HevyExercise {
        private Integer index;
        private String title;
        private String notes;

        @JsonProperty("exercise_template_id")
        private String exerciseTemplateId;

        @JsonProperty("superset_id")
        private String supersetId;

        private List<HevySet> sets;

        @JsonProperty("rest_seconds")
        private Integer restSeconds;
    }

    @Data
    public static class HevySet {
        private Integer index;
        private String type;

        @JsonProperty("weight_kg")
        private Double weightKg;

        private Integer reps;

        @JsonProperty("distance_meters")
        private Double distanceMeters;

        @JsonProperty("duration_seconds")
        private Integer durationSeconds;

        @JsonProperty("custom_metric")
        private String customMetric;

        @JsonProperty("rep_range")
        private RepRange repRange;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RepRange {
            private Integer start;
            private Integer end;
        }
    }
}