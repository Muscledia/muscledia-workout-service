package com.muscledia.workout_service.exception;

import lombok.Getter;

@Getter
public class ExerciseNotFoundException extends WorkoutServiceException {
    private final String exerciseId;
    private final Integer exerciseIndex;

    public ExerciseNotFoundException(String message) {
        super(message);
        this.exerciseId = null;
        this.exerciseIndex = null;
    }

    public ExerciseNotFoundException(String message, String exerciseId) {
        super(message);
        this.exerciseId = exerciseId;
        this.exerciseIndex = null;
    }

    public ExerciseNotFoundException(String message, Integer exerciseIndex) {
        super(message);
        this.exerciseId = null;
        this.exerciseIndex = exerciseIndex;
    }

}
