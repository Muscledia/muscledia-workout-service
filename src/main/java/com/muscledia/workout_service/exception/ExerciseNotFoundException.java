package com.muscledia.workout_service.exception;

public class ExerciseNotFoundException extends RuntimeException {
  public ExerciseNotFoundException(String message) {
    super(message);
  }
}
