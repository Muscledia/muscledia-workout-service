package com.muscledia.workout_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when concurrent modification occurs
 */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class ConcurrentModificationException extends WorkoutServiceException {
    private final String entityId;
    private final Long expectedVersion;
    private final Long actualVersion;

    public ConcurrentModificationException(String message, String entityId, Long expectedVersion, Long actualVersion) {
        super(message);
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

}
