package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends WorkoutServiceException {
    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String message, String resourceType, String resourceId) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

}