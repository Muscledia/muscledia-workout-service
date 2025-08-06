package com.muscledia.workout_service.exception;

import lombok.Getter;

@Getter
public class InvalidSetDataException extends WorkoutServiceException {
  private final String fieldName;
  private final Object fieldValue;

  public InvalidSetDataException(String message) {
    super(message);
    this.fieldName = null;
    this.fieldValue = null;
  }

  public InvalidSetDataException(String message, String fieldName, Object fieldValue) {
    super(message);
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

  public String getFieldName() { return fieldName; }
  public Object getFieldValue() { return fieldValue; }
}
