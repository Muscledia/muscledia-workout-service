package com.muscledia.workout_service.exception;

public class WorkoutNotFoundException extends RuntimeException {
  public WorkoutNotFoundException(String message) {
    super(message);
  }
}
