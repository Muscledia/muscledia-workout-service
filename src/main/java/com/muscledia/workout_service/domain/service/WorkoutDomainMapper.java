package com.muscledia.workout_service.domain.service;

import com.muscledia.workout_service.domain.model.ExerciseData;
import com.muscledia.workout_service.domain.model.SetData;
import com.muscledia.workout_service.domain.model.WorkoutData;
import com.muscledia.workout_service.domain.model.WorkoutExerciseData;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain Mapper - Translates between Infrastructure and Domain layers
 * Follows DDD principle of keeping domain objects free from infrastructure concerns
 */
@Service
@Slf4j
public class WorkoutDomainMapper {
    /**
     * Convert persistence Workout to domain WorkoutData
     */
    public WorkoutData toDomainWorkout(Workout workout) {
        if (workout == null) {
            return null;
        }

        log.debug("Mapping Workout {} to domain", workout.getId());

        return new WorkoutData(
                workout.getId(),
                workout.getUserId(),
                workout.getWorkoutName(),
                workout.getWorkoutType(),
                workout.getWorkoutDate(),
                workout.getStartedAt(),
                workout.getCompletedAt(),
                toDomainExercises(workout.getExercises()),
                workout.getNotes(),
                workout.getRating(),
                workout.getTags() != null ? new ArrayList<>(workout.getTags()) : new ArrayList<>()
        );
    }

    /**
     * Convert list of persistence WorkoutExercise to domain ExerciseData
     */
    public List<ExerciseData> toDomainExercises(List<WorkoutExercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return new ArrayList<>();
        }

        return exercises.stream()
                .map(this::toDomainExercise)
                .collect(Collectors.toList());
    }

    /**
     * Convert persistence WorkoutExercise to domain ExerciseData
     */
    private ExerciseData toDomainExercise(WorkoutExercise exercise) {
        if (exercise == null) {
            return null;
        }

        return new ExerciseData(
                exercise.getExerciseId(),
                exercise.getExerciseName(),
                exercise.getExerciseCategory(),
                exercise.getPrimaryMuscleGroup(),
                exercise.getSecondaryMuscleGroups() != null ?
                        new ArrayList<>(exercise.getSecondaryMuscleGroups()) : new ArrayList<>(),
                toDomainSets(exercise.getSets()),
                exercise.getExerciseOrder()
        );
    }

    /**
     * Convert list of persistence WorkoutSet to domain SetData
     */
    public List<SetData> toDomainSets(List<WorkoutSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return new ArrayList<>();
        }

        return sets.stream()
                .map(this::toDomainSet)
                .collect(Collectors.toList());
    }

    /**
     * Convert persistence WorkoutSet to domain SetData
     */
    private SetData toDomainSet(WorkoutSet set) {
        if (set == null) {
            return null;
        }

        return new SetData(
                set.getSetNumber(),
                set.getWeightKg(),
                set.getReps(),
                set.getDurationSeconds(),
                set.getDistanceMeters(),
                set.getRestSeconds(),
                set.getRpe(),
                Boolean.TRUE.equals(set.getCompleted()),
                set.getSetType(),  // Use enum directly
                set.getCompletedAt()
        );
    }

    /**
     * Convert domain WorkoutData back to persistence Workout
     * Used when domain operations need to be persisted
     */
    public Workout toPersistenceWorkout(WorkoutData workoutData, Workout existingWorkout) {
        if (workoutData == null) {
            return existingWorkout;
        }

        log.debug("Mapping domain WorkoutData {} to persistence", workoutData.getId());

        // Update existing workout with domain changes
        // This preserves infrastructure-specific fields while applying domain updates
        if (existingWorkout != null) {
            existingWorkout.setWorkoutName(workoutData.getWorkoutName());
            existingWorkout.setWorkoutType(workoutData.getWorkoutType());
            existingWorkout.setNotes(workoutData.getNotes());
            existingWorkout.setRating(workoutData.getRating());
            existingWorkout.setTags(workoutData.getTags() != null ?
                    new ArrayList<>(workoutData.getTags()) : new ArrayList<>());

            // Update exercises if needed
            updatePersistenceExercises(existingWorkout, workoutData.getExercises());

            return existingWorkout;
        }

        // Create new workout from domain data (rare case)
        return createNewWorkoutFromDomain(workoutData);
    }

    /**
     * Update exercises on persistence workout from domain data
     */
    private void updatePersistenceExercises(Workout workout, List<ExerciseData> domainExercises) {
        if (domainExercises == null) {
            return;
        }

        // This is a simplified approach - in practice you might want more sophisticated merging
        if (workout.getExercises() != null && domainExercises.size() == workout.getExercises().size()) {
            for (int i = 0; i < domainExercises.size(); i++) {
                ExerciseData domainExercise = domainExercises.get(i);
                WorkoutExercise persistenceExercise = workout.getExercises().get(i);

                // Update sets completion status from domain
                updatePersistenceSets(persistenceExercise, domainExercise.getSets());
            }
        }
    }

    /**
     * Update sets on persistence exercise from domain data
     */
    private void updatePersistenceSets(WorkoutExercise exercise, List<SetData> domainSets) {
        if (domainSets == null || exercise.getSets() == null) {
            return;
        }

        for (int i = 0; i < Math.min(domainSets.size(), exercise.getSets().size()); i++) {
            SetData domainSet = domainSets.get(i);
            WorkoutSet persistenceSet = exercise.getSets().get(i);

            // Update completion status from domain calculations
            persistenceSet.setCompleted(domainSet.isCompleted());
            persistenceSet.setCompletedAt(domainSet.getCompletedAt());
        }
    }

    /**
     * Create new persistence workout from domain data (fallback method)
     */
    private Workout createNewWorkoutFromDomain(WorkoutData workoutData) {
        log.warn("Creating new Workout from domain data - this should be rare in DDD");

        return Workout.builder()
                .id(workoutData.getId())
                .userId(workoutData.getUserId())
                .workoutName(workoutData.getWorkoutName())
                .workoutType(workoutData.getWorkoutType())
                .workoutDate(workoutData.getWorkoutDate())
                .startedAt(workoutData.getStartedAt())
                .completedAt(workoutData.getCompletedAt())
                .notes(workoutData.getNotes())
                .rating(workoutData.getRating())
                .tags(workoutData.getTags() != null ? new ArrayList<>(workoutData.getTags()) : new ArrayList<>())
                .exercises(toPersistenceExercises(workoutData.getExercises()))
                .build();
    }

    /**
     * Convert domain exercises to persistence exercises
     */
    private List<WorkoutExercise> toPersistenceExercises(List<ExerciseData> domainExercises) {
        if (domainExercises == null) {
            return new ArrayList<>();
        }

        return domainExercises.stream()
                .map(this::toPersistenceExercise)
                .collect(Collectors.toList());
    }

    /**
     * Convert domain exercise to persistence exercise
     */
    private WorkoutExercise toPersistenceExercise(ExerciseData exerciseData) {
        WorkoutExercise exercise = new WorkoutExercise();
        exercise.setExerciseId(exerciseData.getExerciseId());
        exercise.setExerciseName(exerciseData.getExerciseName());
        exercise.setExerciseCategory(exerciseData.getCategory());
        exercise.setPrimaryMuscleGroup(exerciseData.getPrimaryMuscleGroup());
        exercise.setSecondaryMuscleGroups(
                exerciseData.getSecondaryMuscleGroups() != null ?
                        new ArrayList<>(exerciseData.getSecondaryMuscleGroups()) : new ArrayList<>()
        );
        exercise.setExerciseOrder(exerciseData.getExerciseOrder());
        exercise.setSets(toPersistenceSets(exerciseData.getSets()));

        return exercise;
    }

    /**
     * Convert domain sets to persistence sets
     */
    private List<WorkoutSet> toPersistenceSets(List<SetData> domainSets) {
        if (domainSets == null) {
            return new ArrayList<>();
        }

        return domainSets.stream()
                .map(this::toPersistenceSet)
                .collect(Collectors.toList());
    }

    /**
     * Convert domain set to persistence set
     */
    private WorkoutSet toPersistenceSet(SetData setData) {
        return WorkoutSet.builder()
                .setNumber(setData.getSetNumber())
                .weightKg(setData.getWeight())
                .reps(setData.getReps())
                .durationSeconds(setData.getDurationSeconds())
                .distanceMeters(setData.getDistanceMeters())
                .restSeconds(setData.getRestSeconds())
                .rpe(setData.getRpe())
                .completed(setData.isCompleted())
                .setType(setData.getSetType())
                .completedAt(setData.getCompletedAt())
                .build();
    }

    /**
     * Utility method to map multiple workouts
     */
    public List<WorkoutData> toDomainWorkouts(List<Workout> workouts) {
        if (workouts == null || workouts.isEmpty()) {
            return new ArrayList<>();
        }

        return workouts.stream()
                .map(this::toDomainWorkout)
                .collect(Collectors.toList());
    }
}
