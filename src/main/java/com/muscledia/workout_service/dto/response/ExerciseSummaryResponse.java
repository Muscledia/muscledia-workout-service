package com.muscledia.workout_service.dto.response;

import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary response for exercise browsing
 *
 * Used when users browse exercises to add to workout plans
 * Contains all information needed for:
 * - Displaying exercise cards
 * - Filtering/searching
 * - Making informed selection decisions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseSummaryResponse {
    /**
     * Unique exercise identifier
     */
    private String id;

    /**
     * Exercise name
     */
    private String name;

    /**
     * Body part targeted (e.g., "waist", "back", "chest", "legs")
     */
    private String bodyPart;

    /**
     * Equipment required (e.g., "barbell", "dumbbell", "body weight")
     */
    private String equipment;

    /**
     * Primary muscle targeted (e.g., "abs", "biceps", "quadriceps")
     */
    private String targetMuscle;

    /**
     * Secondary muscles worked
     * Helps users understand comprehensive muscle engagement
     */
    private List<String> secondaryMuscles;

    /**
     * Exercise difficulty level
     * Helps users select appropriate exercises for their level
     */
    private ExerciseDifficulty difficulty;

    /**
     * Exercise category (e.g., STRENGTH, CARDIO, FLEXIBILITY)
     * Helps categorize and filter exercises
     */
    private ExerciseCategory category;

    /**
     * Brief exercise description
     * Helps users understand what the exercise is
     */
    private String description;

}
