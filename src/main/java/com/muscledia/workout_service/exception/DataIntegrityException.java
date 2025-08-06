package com.muscledia.workout_service.exception;

public class DataIntegrityException extends RuntimeException {
  public DataIntegrityException(String message) {
    super(message);
  }
}
