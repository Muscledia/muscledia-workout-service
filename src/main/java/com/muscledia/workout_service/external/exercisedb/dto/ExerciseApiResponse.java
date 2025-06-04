package com.muscledia.workout_service.external.exercisedb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ExerciseApiResponse {
    private boolean success;
    private ExerciseDataContainer data;
}