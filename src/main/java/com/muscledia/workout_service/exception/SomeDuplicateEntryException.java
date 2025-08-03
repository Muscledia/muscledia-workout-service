package com.muscledia.workout_service.exception;

public class SomeDuplicateEntryException extends RuntimeException {
    public SomeDuplicateEntryException(String message) {
        super(message);
    }
}
