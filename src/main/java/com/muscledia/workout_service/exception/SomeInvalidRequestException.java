package com.muscledia.workout_service.exception;

public class SomeInvalidRequestException extends RuntimeException {
    public SomeInvalidRequestException(String message) {
        super(message);
    }
}
