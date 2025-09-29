package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends WorkoutServiceException {
    /**
     * -- GETTER --
     *  Get the field that caused the validation error
     *
     * @return the field name, or null if not specified
     */
    private final String field;
    /**
     * -- GETTER --
     *  Get the value that was rejected during validation
     *
     * @return the rejected value, or null if not specified
     */
    private final Object rejectedValue;

    // Existing constructor (single message) - maintains backward compatibility
    public ValidationException(String message) {
        super(message);
        this.field = null;
        this.rejectedValue = null;
    }

    // New constructor (field + message) - what your ExerciseService needs
    public ValidationException(String field, String message) {
        super(String.format("Validation failed for field '%s': %s", field, message));
        this.field = field;
        this.rejectedValue = null;
    }

    // Constructor with field, message, and rejected value
    public ValidationException(String field, String message, Object rejectedValue) {
        super(String.format("Validation failed for field '%s': %s (rejected value: %s)", field, message, rejectedValue));
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    // Constructor with cause
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.field = null;
        this.rejectedValue = null;
    }

    // Constructor with field, message, and cause
    public ValidationException(String field, String message, Throwable cause) {
        super(String.format("Validation failed for field '%s': %s", field, message), cause);
        this.field = field;
        this.rejectedValue = null;
    }

    // Constructor with field, message, rejected value, and cause
    public ValidationException(String field, String message, Object rejectedValue, Throwable cause) {
        super(String.format("Validation failed for field '%s': %s (rejected value: %s)", field, message, rejectedValue), cause);
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    /**
     * Check if this exception has a specific field associated with it
     * @return true if a field was specified, false otherwise
     */
    public boolean hasField() {
        return field != null && !field.trim().isEmpty();
    }

    /**
     * Check if this exception has a rejected value associated with it
     * @return true if a rejected value was specified, false otherwise
     */
    public boolean hasRejectedValue() {
        return rejectedValue != null;
    }

}