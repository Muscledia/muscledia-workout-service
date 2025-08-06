package com.muscledia.workout_service.exception;

public class InsufficientPermissionException extends RuntimeException {
  public InsufficientPermissionException(String message) {
    super(message);
  }
}
