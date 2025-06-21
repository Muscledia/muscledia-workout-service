package com.muscledia.workout_service.service;

import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutPlanService {
    private final WorkoutPlanRepository workoutPlanRepository;

    public Mono<WorkoutPlan> findById(String id) {
        return workoutPlanRepository.findById(id)
                .doOnNext(plan -> log.debug("Found workout plan: {}", plan.getTitle()))
                .switchIfEmpty(Mono.error(new RuntimeException("Workout plan not found with id: " + id)));
    }

    public Flux<WorkoutPlan> searchByTitle(String title) {
        return workoutPlanRepository.findByTitleContainingIgnoreCase(title)
                .doOnComplete(() -> log.debug("Retrieved workout plans matching title: {}", title));
    }

    public Flux<WorkoutPlan> findPublicWorkoutPlans() {
        return workoutPlanRepository.findByIsPublicTrue()
                .doOnComplete(() -> log.debug("Retrieved public workout plans"));
    }

    public Flux<WorkoutPlan> findByCreator(Long userId) {
        return workoutPlanRepository.findByCreatedBy(userId)
                .doOnComplete(() -> log.debug("Retrieved workout plans for user: {}", userId));
    }

    public Flux<WorkoutPlan> findPopularWorkoutPlans(Pageable pageable) {
        return workoutPlanRepository.findByIsPublicTrueOrderByUsageCountDesc(pageable)
                .doOnComplete(() -> log.debug("Retrieved popular workout plans"));
    }

    public Flux<WorkoutPlan> findRecentWorkoutPlans(Pageable pageable) {
        return workoutPlanRepository.findByIsPublicTrueOrderByCreatedAtDesc(pageable)
                .doOnComplete(() -> log.debug("Retrieved recent workout plans"));
    }

    public Flux<WorkoutPlan> findByTargetMuscleGroup(String muscleGroup) {
        return workoutPlanRepository.findByTargetMuscleGroup(muscleGroup)
                .doOnComplete(() -> log.debug("Retrieved workout plans for muscle group: {}", muscleGroup));
    }

    public Flux<WorkoutPlan> findByRequiredEquipment(String equipment) {
        return workoutPlanRepository.findByRequiredEquipment(equipment)
                .doOnComplete(() -> log.debug("Retrieved workout plans for equipment: {}", equipment));
    }

    public Flux<WorkoutPlan> searchPublicWorkouts(String searchTerm) {
        return workoutPlanRepository.searchPublicWorkouts(searchTerm)
                .doOnComplete(() -> log.debug("Retrieved public workout plans matching: {}", searchTerm));
    }

    public Flux<WorkoutPlan> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return workoutPlanRepository.findByCreatedAtBetween(start, end)
                .doOnComplete(() -> log.debug("Retrieved workout plans between {} and {}", start, end));
    }

    public Flux<WorkoutPlan> findByExercise(String exerciseTemplateId) {
        return workoutPlanRepository.findByExerciseTemplateId(exerciseTemplateId)
                .doOnComplete(() -> log.debug("Retrieved workout plans containing exercise: {}", exerciseTemplateId));
    }

    public Flux<WorkoutPlan> findByDurationRange(Integer minDuration, Integer maxDuration) {
        return workoutPlanRepository.findByEstimatedDurationMinutesBetween(minDuration, maxDuration)
                .doOnComplete(() -> log.debug("Retrieved workout plans with duration between {} and {} minutes",
                        minDuration, maxDuration));
    }

    public Flux<WorkoutPlan> findByFolder(Long folderId) {
        return workoutPlanRepository.findByFolderId(folderId)
                .doOnComplete(() -> log.debug("Retrieved workout plans for folder: {}", folderId));
    }

    public Mono<WorkoutPlan> save(WorkoutPlan workoutPlan) {
        if (workoutPlan.getCreatedAt() == null) {
            workoutPlan.setCreatedAt(LocalDateTime.now());
        }
        workoutPlan.setUpdatedAt(LocalDateTime.now());

        return workoutPlanRepository.save(workoutPlan)
                .doOnSuccess(saved -> log.debug("Saved workout plan: {}", saved.getTitle()));
    }

    public Mono<WorkoutPlan> saveToPersonalCollection(String publicWorkoutPlanId, Long userId) {
        return findById(publicWorkoutPlanId)
                .flatMap(publicPlan -> {
                    // Create a copy for the user's personal collection
                    WorkoutPlan personalPlan = new WorkoutPlan();
                    personalPlan.setTitle(publicPlan.getTitle());
                    personalPlan.setDescription(publicPlan.getDescription());
                    personalPlan.setExercises(publicPlan.getExercises());
                    personalPlan.setEstimatedDurationMinutes(publicPlan.getEstimatedDurationMinutes());
                    personalPlan.setIsPublic(false); // Personal copy
                    personalPlan.setCreatedBy(userId);
                    personalPlan.setUsageCount(0L);
                    personalPlan.setCreatedAt(LocalDateTime.now());

                    // Increment usage count on original public plan
                    publicPlan.setUsageCount(publicPlan.getUsageCount() + 1);

                    return workoutPlanRepository.save(publicPlan)
                            .then(workoutPlanRepository.save(personalPlan));
                })
                .doOnSuccess(saved -> log.debug("Saved workout plan to personal collection for user: {}", userId));
    }

    public Flux<WorkoutPlan> findPersonalWorkoutPlans(Long userId) {
        return workoutPlanRepository.findByCreatedByAndIsPublicFalse(userId)
                .doOnComplete(() -> log.debug("Retrieved personal workout plans for user: {}", userId));
    }

    public Mono<WorkoutPlan> incrementUsageCount(String id) {
        return findById(id)
                .flatMap(plan -> {
                    plan.setUsageCount(plan.getUsageCount() + 1);
                    return save(plan);
                })
                .doOnSuccess(plan -> log.debug("Incremented usage count for workout plan: {}", plan.getTitle()));
    }

    public Mono<Void> deleteById(String id) {
        return workoutPlanRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted workout plan with id: {}", id));
    }

    public Flux<WorkoutPlan> findAll() {
        return workoutPlanRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all workout plans"));
    }
}