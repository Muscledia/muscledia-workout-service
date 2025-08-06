package com.muscledia.workout_service.exception;

public class InvalidWorkoutStateException extends RuntimeException {
  public InvalidWorkoutStateException(String message) {
    super(message);
  }
}
