package com.muscledia.workout_service.domain.model;

import com.muscledia.workout_service.domain.vo.Volume;
import lombok.Value;

import java.util.List;

@Value
public class WorkoutExerciseData {
    String exerciseId;
    String exerciseName;
    List<SetData> sets;

    public Volume calculateVolume() {
        return sets.stream()
                .map(SetData::getVolume)
                .reduce(Volume.zero(), Volume::add);
    }
}
