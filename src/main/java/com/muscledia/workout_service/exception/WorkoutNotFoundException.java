package com.muscledia.workout_service.exception;

public class WorkoutNotFoundException extends WorkoutServiceException {
  public WorkoutNotFoundException(String message) {
    super(message);
  }

  public WorkoutNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
