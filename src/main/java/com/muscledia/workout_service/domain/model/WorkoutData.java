package com.muscledia.workout_service.domain.model;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;


/**
 * Pure Domain Model for Workout Data
 * No infrastructure dependencies (no JPA/MongoDB annotations)
 */
@Value
public class WorkoutData {

    String id;
    Long userId;
    String workoutName;
    String workoutType;
    LocalDateTime workoutDate;
    LocalDateTime startedAt;
    LocalDateTime completedAt;
    List<ExerciseData> exercises;
    String notes;
    Integer rating;
    List<String> tags;

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean hasExercises() {
        return exercises != null && !exercises.isEmpty();
    }
}
