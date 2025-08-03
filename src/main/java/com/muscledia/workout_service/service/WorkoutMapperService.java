package com.muscledia.workout_service.service;

import com.muscledia.workout_service.dto.request.CreateWorkoutRequest;
import com.muscledia.workout_service.dto.request.UpdateWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.WorkoutExerciseRequest;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkoutMapperService {

    /**
     * Convert CreateWorkoutRequest to Workout entity
     */
    public Workout toEntity(CreateWorkoutRequest request, Long userId) {
        if (request == null) {
            return null;
        }

        Workout workout = new Workout();
        workout.setUserId(userId);
        workout.setWorkoutDate(request.getWorkoutDate());
        workout.setWorkoutPlanId(request.getWorkoutPlanId());
        workout.setDurationMinutes(request.getDurationMinutes());
        workout.setNotes(request.getNotes());
        workout.setCreatedAt(LocalDateTime.now());
        workout.setUpdatedAt(LocalDateTime.now());

        // Map exercises
        if (request.getExercises() != null) {
            List<WorkoutExercise> exercises = request.getExercises().stream()
                    .map(this::toWorkoutExercise)
                    .collect(Collectors.toList());
            workout.setExercises(exercises);
        }

        // Calculate total volume if not provided
        BigDecimal totalVolume = request.getTotalVolume();
        if (totalVolume == null && workout.getExercises() != null) {
            totalVolume = calculateTotalVolume(workout.getExercises());
        }
        workout.setTotalVolume(totalVolume);

        log.debug("Mapped CreateWorkoutRequest to Workout entity for user: {}", userId);
        return workout;
    }

    /**
     * Update existing Workout entity with UpdateWorkoutRequest data
     */
    public Workout updateEntity(Workout existingWorkout, UpdateWorkoutRequest request) {
        if (request == null || existingWorkout == null) {
            return existingWorkout;
        }

        // Only update fields that are provided (not null)
        if (request.getWorkoutDate() != null) {
            existingWorkout.setWorkoutDate(request.getWorkoutDate());
        }

        if (request.getWorkoutPlanId() != null) {
            existingWorkout.setWorkoutPlanId(request.getWorkoutPlanId());
        }

        if (request.getDurationMinutes() != null) {
            existingWorkout.setDurationMinutes(request.getDurationMinutes());
        }

        if (request.getNotes() != null) {
            existingWorkout.setNotes(request.getNotes());
        }

        // Update exercises if provided
        if (request.getExercises() != null) {
            List<WorkoutExercise> exercises = request.getExercises().stream()
                    .map(this::toWorkoutExercise)
                    .collect(Collectors.toList());
            existingWorkout.setExercises(exercises);
        }

        // Recalculate total volume if exercises were updated or if explicitly provided
        if (request.getExercises() != null || request.getTotalVolume() != null) {
            BigDecimal totalVolume = request.getTotalVolume();
            if (totalVolume == null && existingWorkout.getExercises() != null) {
                totalVolume = calculateTotalVolume(existingWorkout.getExercises());
            }
            existingWorkout.setTotalVolume(totalVolume);
        }

        existingWorkout.setUpdatedAt(LocalDateTime.now());

        log.debug("Updated Workout entity with UpdateWorkoutRequest data");
        return existingWorkout;
    }

    /**
     * Convert WorkoutExerciseRequest to WorkoutExercise embedded document
     */
    private WorkoutExercise toWorkoutExercise(WorkoutExerciseRequest request) {
        if (request == null) {
            return null;
        }

        WorkoutExercise exercise = new WorkoutExercise();
        exercise.setExerciseId(request.getExerciseId());
        exercise.setSets(request.getSets());
        exercise.setReps(request.getReps());
        exercise.setWeight(request.getWeight());
        exercise.setOrder(request.getOrder());
        exercise.setNotes(request.getNotes());

        return exercise;
    }

    /**
     * Calculate total volume from workout exercises
     */
    private BigDecimal calculateTotalVolume(List<WorkoutExercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return exercises.stream()
                .filter(exercise -> exercise.getWeight() != null && exercise.getReps() != null
                        && exercise.getSets() != null)
                .map(exercise -> BigDecimal.valueOf(exercise.getWeight()) // FIX: Convert Double to BigDecimal
                        .multiply(BigDecimal.valueOf(exercise.getReps()))
                        .multiply(BigDecimal.valueOf(exercise.getSets())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Validate that exercise exists (placeholder for future exercise validation)
     */
    public boolean isValidExerciseId(String exerciseId) {
        // TODO: Implement actual exercise validation
        // This would typically check if the exercise exists in the database
        return exerciseId != null && !exerciseId.trim().isEmpty();
    }
}