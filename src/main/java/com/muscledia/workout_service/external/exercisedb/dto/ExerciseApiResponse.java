package com.muscledia.workout_service.external.exercisedb.dto;

import lombok.Data;

@Data
public class ExerciseApiResponse {
    private boolean success;
    private ExerciseDataContainer data;
}