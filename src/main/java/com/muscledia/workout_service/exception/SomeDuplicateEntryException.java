package com.muscledia.workout_service.exception;

import lombok.Getter;

@Getter
public class SomeDuplicateEntryException extends WorkoutServiceException {
    private final String entityType;
    private final String duplicateField;
    private final Object duplicateValue;

    public SomeDuplicateEntryException(String message) {
        super(message);
        this.entityType = null;
        this.duplicateField = null;
        this.duplicateValue = null;
    }

    public SomeDuplicateEntryException(String message, String entityType, String duplicateField, Object duplicateValue) {
        super(message);
        this.entityType = entityType;
        this.duplicateField = duplicateField;
        this.duplicateValue = duplicateValue;
    }

}
