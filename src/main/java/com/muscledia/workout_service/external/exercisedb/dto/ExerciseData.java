package com.muscledia.workout_service.external.exercisedb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ExerciseData {
    @JsonProperty("exerciseId")
    private String exerciseId;

    private String name;

    @JsonProperty("gifUrl")
    private String gifUrl;

    @JsonProperty("targetMuscles")
    private List<String> targetMuscles;

    @JsonProperty("bodyParts")
    private List<String> bodyParts;

    @JsonProperty("equipments")
    private List<String> equipments;

    @JsonProperty("secondaryMuscles")
    private List<String> secondaryMuscles;

    private List<String> instructions;
}