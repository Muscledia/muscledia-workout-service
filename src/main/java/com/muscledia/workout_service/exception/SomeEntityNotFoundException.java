package com.muscledia.workout_service.exception;

public class SomeEntityNotFoundException extends RuntimeException {
    public SomeEntityNotFoundException(String message) {
        super(message);
    }
}
