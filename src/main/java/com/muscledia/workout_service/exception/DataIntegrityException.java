package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when data integrity constraints are violated
 */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DataIntegrityException extends WorkoutServiceException {
    private final String constraintName;

    public DataIntegrityException(String message) {
        super(message);
        this.constraintName = null;
    }

    public DataIntegrityException(String message, String constraintName) {
        super(message);
        this.constraintName = constraintName;
    }

}
