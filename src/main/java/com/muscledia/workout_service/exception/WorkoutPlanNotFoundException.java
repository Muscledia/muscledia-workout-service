package com.muscledia.workout_service.exception;

public class WorkoutPlanNotFoundException extends RuntimeException {
    public WorkoutPlanNotFoundException(String planId) {
        super("Workout plan not found: " + planId);
    }
}
