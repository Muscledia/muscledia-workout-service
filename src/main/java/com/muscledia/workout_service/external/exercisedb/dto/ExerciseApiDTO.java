package com.muscledia.workout_service.external.exercisedb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExerciseApiDTO {
    private String id;
    private String bodyPart;
    private String equipment;
    private String name;
    private String target;
    private List<String> secondaryMuscles;
    private List<String> instructions;
    private String description;
    private String difficulty;
    private String category;
    private List<String> keywords;
}
