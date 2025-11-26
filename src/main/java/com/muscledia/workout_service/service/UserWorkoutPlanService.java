package com.muscledia.workout_service.service;


import com.muscledia.workout_service.dto.request.AddExerciseToPlanRequest;
import com.muscledia.workout_service.dto.request.CreateWorkoutPlanRequest;
import com.muscledia.workout_service.exception.UnauthorizedException;
import com.muscledia.workout_service.exception.WorkoutPlanNotFoundException;
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
 * - Dependencies on abstractions (repositories)
 * - Clear separation of concerns
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserWorkoutPlanService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final ExerciseService exerciseService;

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
        plan.setIsPublic(false); // User plans are private
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
     * - Convert to PlannedExercise
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
                        .map(exercise -> convertToPlannedExercise(exercise, request))
                        .flatMap(plannedExercise -> {
                            // Business logic: Add exercise to plan
                            if (plan.getExercises() == null) {
                                plan.setExercises(new ArrayList<>());
                            }

                            // Set index based on current size
                            plannedExercise.setIndex(plan.getExercises().size());
                            plan.getExercises().add(plannedExercise);

                            // Recalculate estimated duration
                            plan.calculateEstimatedDuration();
                            plan.setUpdatedAt(LocalDateTime.now());

                            return workoutPlanRepository.save(plan);
                        })
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
                .flatMap(plan -> {
                    if (exerciseIndex >= plan.getExercises().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid exercise index"));
                    }

                    PlannedExercise exercise = plan.getExercises().get(exerciseIndex);

                    // Business logic: Update sets with proper indexing
                    for (int i = 0; i < sets.size(); i++) {
                        sets.get(i).setIndex(i);
                    }
                    exercise.setSets(sets);

                    // Recalculate duration
                    plan.calculateEstimatedDuration();
                    plan.setUpdatedAt(LocalDateTime.now());

                    return workoutPlanRepository.save(plan);
                });
    }

    /**
     * Remove exercise from plan
     */
    public Mono<WorkoutPlan> removeExerciseFromPlan(
            Long userId,
            String planId,
            Integer exerciseIndex) {

        return validatePlanOwnership(planId, userId)
                .flatMap(plan -> {
                    if (exerciseIndex >= plan.getExercises().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid exercise index"));
                    }

                    plan.getExercises().remove(exerciseIndex.intValue());

                    // Reindex remaining exercises
                    for (int i = 0; i < plan.getExercises().size(); i++) {
                        plan.getExercises().get(i).setIndex(i);
                    }

                    plan.calculateEstimatedDuration();
                    plan.setUpdatedAt(LocalDateTime.now());

                    return workoutPlanRepository.save(plan);
                });
    }

    /**
     * Get user's personal workout plans
     */
    public Flux<WorkoutPlan> getUserPlans(Long userId) {
        return workoutPlanRepository.findByCreatedByOrderByCreatedAtDesc(userId);
    }

    /**
     * Get single plan with ownership check
     */
    public Mono<WorkoutPlan> getUserPlan(Long userId, String planId) {
        return validatePlanOwnership(planId, userId);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Validate plan ownership
     * SECURITY: Ensure user can only access their own plans
     */
    private Mono<WorkoutPlan> validatePlanOwnership(String planId, Long userId) {
        return workoutPlanRepository.findById(planId)
                .switchIfEmpty(Mono.error(new WorkoutPlanNotFoundException(planId)))
                .flatMap(plan -> {
                    if (!plan.getCreatedBy().equals(userId)) {
                        return Mono.error(new UnauthorizedException(
                                "You don't have permission to modify this workout plan"));
                    }
                    return Mono.just(plan);
                });
    }

    /**
     * Convert Exercise to PlannedExercise
     * BUSINESS LOGIC: Map library exercise to plan template
     */
    private PlannedExercise convertToPlannedExercise(
            Exercise exercise,
            AddExerciseToPlanRequest request) {

        PlannedExercise planned = new PlannedExercise();
        planned.setTitle(exercise.getName());
        planned.setExerciseTemplateId(exercise.getId());
        planned.setNotes(request.getNotes());
        planned.setRestSeconds(request.getRestSeconds() != null ?
                request.getRestSeconds() : 90); // Default 90s rest

        // Initialize with requested sets or default
        List<PlannedExercise.PlannedSet> sets = new ArrayList<>();
        int numSets = request.getNumberOfSets() != null ? request.getNumberOfSets() : 3;

        for (int i = 0; i < numSets; i++) {
            PlannedExercise.PlannedSet set = new PlannedExercise.PlannedSet();
            set.setIndex(i);
            set.setType("normal");

            // Use rep range if provided, otherwise default reps
            if (request.getRepRangeStart() != null && request.getRepRangeEnd() != null) {
                set.setRepRangeStart(request.getRepRangeStart());
                set.setRepRangeEnd(request.getRepRangeEnd());
            } else {
                set.setReps(request.getTargetReps() != null ? request.getTargetReps() : 10);
            }

            // Optional weight
            if (request.getTargetWeight() != null) {
                set.setWeightKg(request.getTargetWeight());
            }

            sets.add(set);
        }

        planned.setSets(sets);
        return planned;
    }
}
