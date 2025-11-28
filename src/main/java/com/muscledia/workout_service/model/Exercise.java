package com.muscledia.workout_service.model;

import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "exercises")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exercise {
    @Id
    private String id;

    @Indexed
    private String externalId; // Original ID from RapidAPI

    @Indexed
    private String name;

    private String bodyPart; // "waist", "back", "chest"

    @Indexed
    private String equipment;

    private String targetMuscle;

    private List<String> secondaryMuscles;

    private List<String> instructions; // Step-by-step array

    private String description;

    @Indexed
    private ExerciseDifficulty difficulty;

    private ExerciseCategory category;

    private List<String> keywords;

    // For future image integration
    private String imageUrl;
    private String videoUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Metadata
    private boolean isActive = true;
    private int usageCount = 0;
}



