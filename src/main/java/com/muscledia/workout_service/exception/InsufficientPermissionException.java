package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when user doesn't have permission
 */
@Getter
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InsufficientPermissionException extends WorkoutServiceException {
    private final String requiredPermission;
    private final String resource;

    public InsufficientPermissionException(String message) {
        super(message);
        this.requiredPermission = null;
        this.resource = null;
    }

    public InsufficientPermissionException(String message, String requiredPermission, String resource) {
        super(message);
        this.requiredPermission = requiredPermission;
        this.resource = resource;
    }

}
