package com.muscledia.workout_service.exception;

public class BusinessRuleViolationException extends RuntimeException {
  public BusinessRuleViolationException(String message) {
    super(message);
  }
}
