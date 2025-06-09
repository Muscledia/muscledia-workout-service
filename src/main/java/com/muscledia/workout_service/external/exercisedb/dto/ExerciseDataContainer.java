package com.muscledia.workout_service.external.exercisedb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ExerciseDataContainer {

    private String previousPage; // Can be null
    private String nextPage; // Can be null
    private int totalPages;
    private int totalExercises;
    private int currentPage;

    @JsonProperty("exercises") // Maps the "exercises" array to a List of ExerciseData
    private List<ExerciseData> exercises;
}
