package com.muscledia.workout_service.service;


import com.muscledia.workout_service.dto.request.AddExerciseToPlanRequest;
import com.muscledia.workout_service.dto.request.CreateWorkoutPlanRequest;
import com.muscledia.workout_service.exception.UnauthorizedException;
import com.muscledia.workout_service.exception.WorkoutPlanNotFoundException;
import com.muscledia.workout_service.mapper.WorkoutPlanMapper;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.embedded.PlannedExercise;
import com.muscledia.workout_service.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing user-created workout plans
 *
 * CLEAN ARCHITECTURE:
 * - Business logic isolated from presentation layer
 * - Dependencies on abstractions (repositories, mappers)
 * - Clear separation of concerns
 * - Uses mapper for data transformation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserWorkoutPlanService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final ExerciseService exerciseService;
    private final WorkoutPlanMapper workoutPlanMapper;

    /**
     * Create a new personal workout plan
     *
     * BUSINESS LOGIC:
     * - Initialize empty exercise list
     * - Set as private by default
     * - Track creator
     */
    public Mono<WorkoutPlan> createPersonalPlan(Long userId, CreateWorkoutPlanRequest request) {
        log.info("Creating personal workout plan for user {}: {}", userId, request.getTitle());

        WorkoutPlan plan = new WorkoutPlan();
        plan.setTitle(request.getTitle());
        plan.setDescription(request.getDescription());
        plan.setExercises(new ArrayList<>());
        plan.setIsPublic(false);
        plan.setCreatedBy(userId);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());

        return workoutPlanRepository.save(plan)
                .doOnSuccess(saved -> log.info("Created workout plan: {}", saved.getId()));
    }

    /**
     * Add exercise from library to workout plan
     *
     * BUSINESS LOGIC:
     * - Verify plan ownership
     * - Fetch exercise details
     * - Use mapper to convert to PlannedExercise
     * - Add to plan
     */
    public Mono<WorkoutPlan> addExerciseToPlan(
            Long userId,
            String planId,
            AddExerciseToPlanRequest request) {

        log.info("Adding exercise {} to plan {} for user {}",
                request.getExerciseId(), planId, userId);

        return validatePlanOwnership(planId, userId)
                .flatMap(plan -> exerciseService.findById(request.getExerciseId())
                        .map(exercise -> workoutPlanMapper.toPlannedExercise(exercise, request))
                        .flatMap(plannedExercise -> addExerciseToPlanInternal(plan, plannedExercise))
                );
    }

    /**
     * Update sets for a planned exercise
     *
     * BUSINESS LOGIC:
     * - Verify plan ownership
     * - Update exercise sets
     * - Recalculate duration
     */
    public Mono<WorkoutPlan> updateExerciseSets(
            Long userId,
            String planId,
            Integer exerciseIndex,
            List<PlannedExercise.PlannedSet> sets) {

        log.info("Updating sets for exercise {} in plan {}", exerciseIndex, planId);

        return validatePlanOwnership(planId, userId)
                .flatMap(plan -> updateSetsInternal(plan, exerciseIndex, sets));
    }

    /**
     * Remove exercise from plan
     */
    public Mono<WorkoutPlan> removeExerciseFromPlan(
            Long userId,
            String planId,
            Integer exerciseIndex) {

        log.info("Removing exercise {} from plan {} for user {}", exerciseIndex, planId, userId);

        return validatePlanOwnership(planId, userId)
                .flatMap(plan -> removeExerciseInternal(plan, exerciseIndex));
    }

    /**
     * Get user's personal workout plans
     */
    public Flux<WorkoutPlan> getUserPlans(Long userId) {
        log.debug("Retrieving workout plans for user {}", userId);
        return workoutPlanRepository.findByCreatedByOrderByCreatedAtDesc(userId);
    }

    /**
     * Get single plan with ownership check
     */
    public Mono<WorkoutPlan> getUserPlan(Long userId, String planId) {
        log.debug("Retrieving workout plan {} for user {}", planId, userId);
        return validatePlanOwnership(planId, userId);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Add exercise to plan (internal business logic)
     */
    private Mono<WorkoutPlan> addExerciseToPlanInternal(WorkoutPlan plan, PlannedExercise plannedExercise) {
        if (plan.getExercises() == null) {
            plan.setExercises(new ArrayList<>());
        }

        plannedExercise.setIndex(plan.getExercises().size());
        plan.getExercises().add(plannedExercise);

        plan.calculateEstimatedDuration();
        plan.setUpdatedAt(LocalDateTime.now());

        return workoutPlanRepository.save(plan)
                .doOnSuccess(saved -> log.info("Added exercise to plan {}, new size: {}",
                        plan.getId(), plan.getExercises().size()));
    }

    /**
     * Update sets for exercise (internal business logic)
     */
    private Mono<WorkoutPlan> updateSetsInternal(
            WorkoutPlan plan,
            Integer exerciseIndex,
            List<PlannedExercise.PlannedSet> sets) {

        if (exerciseIndex >= plan.getExercises().size()) {
            return Mono.error(new IllegalArgumentException(
                    "Invalid exercise index: " + exerciseIndex));
        }

        PlannedExercise exercise = plan.getExercises().get(exerciseIndex);

        // Reindex sets
        for (int i = 0; i < sets.size(); i++) {
            sets.get(i).setIndex(i);
        }
        exercise.setSets(sets);

        plan.calculateEstimatedDuration();
        plan.setUpdatedAt(LocalDateTime.now());

        return workoutPlanRepository.save(plan)
                .doOnSuccess(saved -> log.info("Updated sets for exercise {} in plan {}",
                        exerciseIndex, plan.getId()));
    }

    /**
     * Remove exercise from plan (internal business logic)
     */
    private Mono<WorkoutPlan> removeExerciseInternal(WorkoutPlan plan, Integer exerciseIndex) {
        if (exerciseIndex >= plan.getExercises().size()) {
            return Mono.error(new IllegalArgumentException(
                    "Invalid exercise index: " + exerciseIndex));
        }

        plan.getExercises().remove(exerciseIndex.intValue());
        reindexExercises(plan.getExercises());

        plan.calculateEstimatedDuration();
        plan.setUpdatedAt(LocalDateTime.now());

        return workoutPlanRepository.save(plan)
                .doOnSuccess(saved -> log.info("Removed exercise {} from plan {}, new size: {}",
                        exerciseIndex, plan.getId(), plan.getExercises().size()));
    }

    /**
     * Reindex exercises after removal
     */
    private void reindexExercises(List<PlannedExercise> exercises) {
        for (int i = 0; i < exercises.size(); i++) {
            exercises.get(i).setIndex(i);
        }
    }

    /**
     * Validate plan ownership
     * SECURITY: Ensure user can only access their own plans
     */
    private Mono<WorkoutPlan> validatePlanOwnership(String planId, Long userId) {
        return workoutPlanRepository.findById(planId)
                .switchIfEmpty(Mono.error(new WorkoutPlanNotFoundException(planId)))
                .flatMap(plan -> validateOwnership(plan, userId));
    }

    /**
     * Check ownership and return plan or error
     */
    private Mono<WorkoutPlan> validateOwnership(WorkoutPlan plan, Long userId) {
        if (!plan.getCreatedBy().equals(userId)) {
            return Mono.error(new UnauthorizedException(
                    "You don't have permission to modify this workout plan"));
        }
        return Mono.just(plan);
    }
}
