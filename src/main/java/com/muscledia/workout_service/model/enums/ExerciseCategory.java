package com.muscledia.workout_service.model.enums;

import lombok.Getter;

@Getter
public enum ExerciseCategory {
    STRENGTH("strength"),
    CARDIO("cardio"),
    FLEXIBILITY("flexibility"),
    PLYOMETRICS("plyometrics");

    private final String value;

    ExerciseCategory(String value) {
        this.value = value;
    }
}
