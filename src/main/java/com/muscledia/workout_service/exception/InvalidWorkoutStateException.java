package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when trying to perform an operation on a workout in an invalid state
 */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidWorkoutStateException extends WorkoutServiceException {
    private final String currentState;
    private final String requiredState;

    public InvalidWorkoutStateException(String message) {
        super(message);
        this.currentState = null;
        this.requiredState = null;
    }

    public InvalidWorkoutStateException(String message, String currentState, String requiredState) {
        super(message);
        this.currentState = currentState;
        this.requiredState = requiredState;
    }

}
