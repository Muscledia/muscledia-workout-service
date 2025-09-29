package com.muscledia.workout_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "routine_folders")
public class RoutineFolder {
    @Id
    private String id;

    @Field("hevy_id")
    private Long hevyId;

    @Field("folder_index")
    private Integer folderIndex;

    @Indexed
    private String title;

    @Field("workout_plan_ids")
    private List<String> workoutPlanIds = new ArrayList<>();

    @Field("difficulty_level")
    private String difficultyLevel; // BEGINNER, INTERMEDIATE, ADVANCED - extracted from title

    @Field("equipment_type")
    private String equipmentType; // DUMBBELLS, GYM_EQUIPMENT, EQUIPMENT_FREE - extracted from title

    @Field("workout_split")
    private String workoutSplit; // FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS - extracted from title

    @Field("is_public")
    private Boolean isPublic = true;

    @Field("created_by")
    private Long createdBy = 1L; // System user for public folders, user ID for personal folders

    @Field("usage_count")
    private Long usageCount = 0L;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;


    /**
     * FIXED: Add workout plan ID to the list
     */
    public void addWorkoutPlanId(String workoutPlanId) {
        if (this.workoutPlanIds == null) {
            this.workoutPlanIds = new ArrayList<>();
        }
        if (!this.workoutPlanIds.contains(workoutPlanId)) {
            this.workoutPlanIds.add(workoutPlanId);
        }
    }

    /**
     * FIXED: Remove workout plan ID from the list
     */
    public void removeWorkoutPlanId(String workoutPlanId) {
        if (this.workoutPlanIds != null) {
            this.workoutPlanIds.remove(workoutPlanId);
        }
    }

    /**
     * Get workout plan count
     */
    public int getWorkoutPlanCount() {
        return workoutPlanIds != null ? workoutPlanIds.size() : 0;
    }

    /**
     * Check if this is a personal folder
     */
    public boolean isPersonal() {
        return Boolean.FALSE.equals(isPublic);
    }

    /**
     * Check if this is a public folder (override the Lombok generated method)
     */
    public boolean getIsPublic() {
        return Boolean.TRUE.equals(isPublic);
    }

    // Helper method to parse metadata from title
    public void parseMetadataFromTitle() {
        String upperTitle = title.toUpperCase();

        // Parse difficulty level
        if (upperTitle.contains("BEGINNER")) {
            this.difficultyLevel = "BEGINNER";
        } else if (upperTitle.contains("INTERMEDIATE")) {
            this.difficultyLevel = "INTERMEDIATE";
        } else if (upperTitle.contains("ADVANCED")) {
            this.difficultyLevel = "ADVANCED";
        }

        // Parse equipment type
        if (upperTitle.contains("DUMBBELLS")) {
            this.equipmentType = "DUMBBELLS";
        } else if (upperTitle.contains("GYM EQUIPMENT")) {
            this.equipmentType = "GYM_EQUIPMENT";
        } else if (upperTitle.contains("EQUIPMENT-FREE")) {
            this.equipmentType = "EQUIPMENT_FREE";
        }

        // Parse workout split
        if (upperTitle.contains("FULL-BODY") || upperTitle.contains("FULL BODY")) {
            this.workoutSplit = "FULL_BODY";
        } else if (upperTitle.contains("UPPER/LOWER") || upperTitle.contains("UPPER LOWER")) {
            this.workoutSplit = "UPPER_LOWER";
        } else if (upperTitle.contains("PUSH/PULL/LEGS") || upperTitle.contains("PUSH PULL LEGS")) {
            this.workoutSplit = "PUSH_PULL_LEGS";
        }
    }
}