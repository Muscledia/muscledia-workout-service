package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when attempting operations on a workout session
 * that is not in the correct state.
 *
 * This exception is thrown when:
 * - Attempting to log sets on a workout that is not IN_PROGRESS
 * - Attempting to update sets on a completed workout
 * - Any operation requiring active workout session status
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
