package com.muscledia.workout_service.model;

import com.muscledia.workout_service.model.embedded.PlannedExercise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "workout_plans")
public class WorkoutPlan {
    @Id
    private String id;

    @Indexed
    private String title;

    @Field("folder_id")
    private String folderId;

    private String description;

    private List<PlannedExercise> exercises;

    @Field("estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Field("is_public")
    private Boolean isPublic = true;

    /**
     * Unique Identifier for manually created routines.
     * true = Created via 'New Routine' builder
     * false = Cloned from Public Routine / Saved with Folder
     */
    @Field("is_custom")
    private Boolean isCustom = false;

    @Field("created_by")
    private Long createdBy = 1L;

    @Field("usage_count")
    private Long usageCount = 0L;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    public void calculateEstimatedDuration() {
        if (exercises == null || exercises.isEmpty()) {
            this.estimatedDurationMinutes = 0;
            return;
        }

        int totalRestSeconds = exercises.stream()
                .mapToInt(ex -> {
                    int sets = ex.getSets() != null ? ex.getSets().size() : 0;
                    int restSeconds = ex.getRestSeconds() != null ? ex.getRestSeconds() : 0;
                    return (sets - 1) * restSeconds;
                })
                .sum();

        this.estimatedDurationMinutes = (totalRestSeconds / 60) + (exercises.size() * 5);
    }
}