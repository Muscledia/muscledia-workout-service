package com.muscledia.workout_service.external.hevy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class HevyApiResponse {
    private Integer page;

    @JsonProperty("page_count")
    private Integer pageCount;

    private List<HevyWorkoutRoutine> routines;
}