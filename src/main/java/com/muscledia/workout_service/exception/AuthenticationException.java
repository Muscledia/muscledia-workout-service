package com.muscledia.workout_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when authentication fails
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthenticationException extends WorkoutServiceException {
    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
