package com.muscledia.workout_service.mapper;

import com.muscledia.workout_service.domain.service.WorkoutCalculationService;
import com.muscledia.workout_service.domain.vo.WorkoutMetrics;
import com.muscledia.workout_service.dto.request.embedded.AddExerciseRequest;
import com.muscledia.workout_service.dto.response.WorkoutExerciseResponse;
import com.muscledia.workout_service.dto.response.WorkoutPlanSummaryResponse;
import com.muscledia.workout_service.dto.response.WorkoutResponse;
import com.muscledia.workout_service.dto.response.WorkoutSetResponse;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting Workout entities to DTOs
 * Integrates with CalculationService to provide enriched responses
 */
@Component
@RequiredArgsConstructor
public class WorkoutMapper {

    private final WorkoutCalculationService calculationService;

    /**
     * Convert Workout entity to full WorkoutResponse DTO
     */
    public WorkoutResponse toResponse(Workout workout) {
        if (workout == null) return null;

        // Calculate live metrics
        WorkoutMetrics metrics = calculationService.calculateWorkoutMetrics(workout);
        List<String> muscleGroups = calculationService.getWorkedMuscleGroups(workout);
        Integer calories = calculationService.estimateCaloriesBurned(workout);

        return WorkoutResponse.builder()
                .id(workout.getId())
                .userId(workout.getUserId())
                .workoutName(workout.getWorkoutName())
                .workoutPlanId(workout.getWorkoutPlanId())
                .workoutType(workout.getWorkoutType())
                .status(workout.getStatus() != null ? workout.getStatus().toString() : "UNKNOWN")
                .startedAt(workout.getStartedAt() != null ? workout.getStartedAt().toString() : null)
                .completedAt(workout.getCompletedAt() != null ? workout.getCompletedAt().toString() : null)
                .durationMinutes(workout.getDurationMinutes())
                .exercises(toExerciseResponseList(workout.getExercises()))
                .metrics(WorkoutResponse.WorkoutMetrics.builder()
                        .totalVolume(metrics.getTotalVolume().getValue())
                        .totalSets(metrics.getTotalSets())
                        .totalReps(metrics.getTotalReps())
                        .caloriesBurned(calories)
                        .workedMuscleGroups(muscleGroups)
                        .build())
                .context(WorkoutResponse.WorkoutContext.builder()
                        .location(workout.getLocation())
                        .notes(workout.getNotes())
                        .rating(workout.getRating())
                        .tags(workout.getTags() != null ? workout.getTags() : new ArrayList<>())
                        .build())
                .build();
    }

    /**
     * Convert WorkoutPlan to Summary DTO
     */
    public WorkoutPlanSummaryResponse toPlanSummary(WorkoutPlan plan) {
        if (plan == null) return null;

        return WorkoutPlanSummaryResponse.builder()
                .id(plan.getId())
                .title(plan.getTitle())
                .description(plan.getDescription())
                .exerciseCount(plan.getExercises() != null ? plan.getExercises().size() : 0)
                .estimatedDurationMinutes(plan.getEstimatedDurationMinutes())
                .workoutType("STRENGTH") // Could be derived from plan metadata
                .isPublic(plan.getIsPublic())
                .usageCount(plan.getUsageCount())
                .createdAt(plan.getCreatedAt())
                // In a real app, these would be extracted from the plan's exercises
                .targetMuscleGroups(Collections.emptyList())
                .requiredEquipment(Collections.emptyList())
                .build();
    }

    /**
     * Convert Request to Entity (for adding exercises)
     */
    public WorkoutExercise toEntity(AddExerciseRequest request) {
        if (request == null) return null;

        return WorkoutExercise.builder()
                .exerciseId(request.getExerciseId())
                .exerciseName(request.getExerciseName())
                .exerciseCategory(request.getExerciseCategory())
                .primaryMuscleGroup(request.getPrimaryMuscleGroup())
                .secondaryMuscleGroups(request.getSecondaryMuscleGroups() != null ?
                        request.getSecondaryMuscleGroups() : new ArrayList<>())
                .equipment(request.getEquipment())
                .notes(request.getNotes())
                .sets(new ArrayList<>())
                .build();
    }

    // ==================== INTERNAL HELPER METHODS ====================

    private List<WorkoutExerciseResponse> toExerciseResponseList(List<WorkoutExercise> exercises) {
        if (exercises == null) return new ArrayList<>();
        return exercises.stream()
                .map(this::toExerciseResponse)
                .collect(Collectors.toList());
    }

    private WorkoutExerciseResponse toExerciseResponse(WorkoutExercise exercise) {
        // Calculate per-exercise metrics using CalculationService
        BigDecimal totalVolume = calculationService.calculateExerciseTotalVolume(exercise);
        Integer totalReps = calculationService.calculateExerciseTotalReps(exercise);
        BigDecimal maxWeight = calculationService.calculateExerciseMaxWeight(exercise);
        Double averageRpe = calculationService.calculateExerciseAverageRpe(exercise);
        Long completedSets = calculationService.calculateCompletedSetsCount(exercise);

        return WorkoutExerciseResponse.builder()
                .exerciseId(exercise.getExerciseId())
                .exerciseName(exercise.getExerciseName())
                .exerciseOrder(exercise.getExerciseOrder())
                .exerciseCategory(exercise.getExerciseCategory())
                .primaryMuscleGroup(exercise.getPrimaryMuscleGroup())
                .secondaryMuscleGroups(exercise.getSecondaryMuscleGroups() != null ?
                        exercise.getSecondaryMuscleGroups() : new ArrayList<>())
                .equipment(exercise.getEquipment())
                .sets(toSetResponseList(exercise.getSets()))
                .notes(exercise.getNotes())
                .startedAt(exercise.getStartedAt() != null ? exercise.getStartedAt().toString() : null)
                .completedAt(exercise.getCompletedAt() != null ? exercise.getCompletedAt().toString() : null)
                .totalVolume(totalVolume)
                .totalReps(totalReps)
                .maxWeight(maxWeight)
                .averageRpe(averageRpe)
                .completedSets(completedSets.intValue())
                .build();
    }

    private List<WorkoutSetResponse> toSetResponseList(List<WorkoutSet> sets) {
        if (sets == null) return new ArrayList<>();
        return sets.stream()
                .map(this::toSetResponse)
                .collect(Collectors.toList());
    }

    private WorkoutSetResponse toSetResponse(WorkoutSet set) {
        BigDecimal volume = calculationService.calculateSetVolume(set);

        return WorkoutSetResponse.builder()
                .setNumber(set.getSetNumber())
                .weightKg(set.getWeightKg())
                .reps(set.getReps())
                .durationSeconds(set.getDurationSeconds())
                .distanceMeters(set.getDistanceMeters())
                .restSeconds(set.getRestSeconds())
                .rpe(set.getRpe())
                .completed(set.getCompleted())
                .setType(set.getSetType())
                .notes(set.getNotes())
                .startedAt(set.getStartedAt() != null ? set.getStartedAt().toString() : null)
                .completedAt(set.getCompletedAt() != null ? set.getCompletedAt().toString() : null)
                .volume(volume)
                .personalRecords(set.getPersonalRecords())
                .build();
    }
}