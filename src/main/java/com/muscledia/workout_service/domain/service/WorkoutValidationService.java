package com.muscledia.workout_service.domain.service;


import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service for workout validation
 * Handles business rule validation
 */
@Service
public class WorkoutValidationService {

    /**
     * Validate workout for creation/starting
     */
    public ValidationResult validateForCreation(Workout workout) {
        List<String> errors = new ArrayList<>();

        if (workout == null) {
            errors.add("Workout cannot be null");
            return ValidationResult.invalid(errors);
        }

        if (workout.getUserId() == null) {
            errors.add("User ID is required");
        }

        if (workout.getWorkoutName() == null || workout.getWorkoutName().trim().isEmpty()) {
            errors.add("Workout name is required");
        }

        if (workout.getWorkoutDate() == null) {
            errors.add("Workout date is required");
        } else if (workout.getWorkoutDate().isAfter(LocalDateTime.now().plusHours(24))) {
            errors.add("Cannot create workout more than 24 hours in the future");
        }

        if (workout.getWorkoutType() == null || workout.getWorkoutType().trim().isEmpty()) {
            errors.add("Workout type is required");
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /**
     * Validate workout for completion
     */
    public ValidationResult validateForCompletion(Workout workout) {
        List<String> errors = new ArrayList<>();

        if (workout == null) {
            errors.add("Workout cannot be null");
            return ValidationResult.invalid(errors);
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            errors.add("Workout must be in progress to be completed");
        }

        if (workout.getExercises() == null || workout.getExercises().isEmpty()) {
            errors.add("Workout must have at least one exercise to be completed");
        }

        if (workout.getStartedAt() == null) {
            errors.add("Workout must have a start time");
        }

        // Check if workout has been running for an unreasonable amount of time
        if (workout.getStartedAt() != null &&
                workout.getStartedAt().isBefore(LocalDateTime.now().minusHours(12))) {
            errors.add("Workout has been active for more than 12 hours - please verify timing");
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /**
     * Validate workout can be started
     */
    public ValidationResult validateForStart(Workout workout) {
        List<String> errors = new ArrayList<>();

        if (workout == null) {
            errors.add("Workout cannot be null");
            return ValidationResult.invalid(errors);
        }

        if (workout.getStatus() != WorkoutStatus.PLANNED && workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            errors.add("Only planned workouts can be started");
        }

        if (workout.getWorkoutDate() != null &&
                workout.getWorkoutDate().isAfter(LocalDateTime.now().plusHours(1))) {
            errors.add("Cannot start workout too far in the future");
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /**
     * Validate workout for set operations (logging, updating, deleting sets)
     */
    public ValidationResult validateForSetOperations(Workout workout) {
        List<String> errors = new ArrayList<>();

        if (workout == null) {
            errors.add("Workout cannot be null");
            return ValidationResult.invalid(errors);
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            errors.add(String.format(
                    "Set operations can only be performed on IN_PROGRESS workout sessions. Current status: %s",
                    workout.getStatus()
            ));
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /**
     * Check if workout can be modified
     */
    public ValidationResult validateForModification(Workout workout) {
        List<String> errors = new ArrayList<>();

        if (workout == null) {
            errors.add("Workout cannot be null");
            return ValidationResult.invalid(errors);
        }

        if (workout.getStatus() == WorkoutStatus.COMPLETED) {
            errors.add("Cannot modify completed workouts");
        }

        if (workout.getStatus() == WorkoutStatus.CANCELLED) {
            errors.add("Cannot modify cancelled workouts");
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /**
     * Check if workout is in valid state
     */
    public boolean isValidWorkout(Workout workout) {
        return workout != null
                && workout.getUserId() != null
                && workout.getWorkoutDate() != null
                && workout.getStatus() != null
                && (workout.getWorkoutName() != null && !workout.getWorkoutName().trim().isEmpty());
    }

    /**
     * Validate that a workout can be deleted
     */
    public ValidationResult validateForDeletion(Workout workout) {
        List<String> errors = new ArrayList<>();

        if (workout == null) {
            errors.add("Workout cannot be null");
            return ValidationResult.invalid(errors);
        }

        // Generally allow deletion of any workout, but you might want to add rules
        // For example: prevent deletion of workouts older than X days
        // or prevent deletion of workouts that are part of a training program

        return ValidationResult.valid();
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? List.copyOf(errors) : List.of();
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, List.of(error));
        }

        public boolean isValid() { return valid; }
        public boolean isInvalid() { return !valid; }
        public List<String> getErrors() { return errors; }
        public String getErrorMessage() { return String.join(", ", errors); }

        @Override
        public String toString() {
            return valid ? "ValidationResult{valid=true}" :
                    "ValidationResult{valid=false, errors=" + errors + "}";
        }
    }
}
