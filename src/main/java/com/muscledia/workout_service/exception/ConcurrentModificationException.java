package com.muscledia.workout_service.exception;

public class ConcurrentModificationException extends RuntimeException {
  public ConcurrentModificationException(String message) {
    super(message);
  }
}
