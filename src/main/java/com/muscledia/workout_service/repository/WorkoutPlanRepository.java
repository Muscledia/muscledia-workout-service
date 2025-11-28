package com.muscledia.workout_service.repository;

import com.muscledia.workout_service.model.WorkoutPlan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface WorkoutPlanRepository extends ReactiveMongoRepository<WorkoutPlan, String> {
    // Find by name (case-insensitive, partial match)
    Flux<WorkoutPlan> findByTitleContainingIgnoreCase(String title);

    // Find public workout plans
    Flux<WorkoutPlan> findByIsPublicTrue();

    // Find by creator
    Flux<WorkoutPlan> findByCreatedBy(Long userId);

    // Find personal workout plans (private, created by user)
    Flux<WorkoutPlan> findByCreatedByAndIsPublicFalse(Long userId);

    // Find popular workout plans (by usage count)
    Flux<WorkoutPlan> findByIsPublicTrueOrderByUsageCountDesc(Pageable pageable);

    // Find recently created public workout plans
    Flux<WorkoutPlan> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    // Find by target muscle groups
    @Query("{'targetMuscleGroups': ?0}")
    Flux<WorkoutPlan> findByTargetMuscleGroup(String muscleGroup);

    // Find by required equipment
    @Query("{'requiredEquipment': ?0}")
    Flux<WorkoutPlan> findByRequiredEquipment(String equipment);

    // Search by title or tags
    @Query("{'isPublic': true, '$or': [{'title': {'$regex': ?0, '$options': 'i'}}, {'tags': {'$regex': ?0, '$options': 'i'}}]}")
    Flux<WorkoutPlan> searchPublicWorkouts(String searchTerm);

    // Find workouts created between dates
    Flux<WorkoutPlan> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Find workouts that contain specific exercises
    @Query("{'exercises.exerciseTemplateId': ?0}")
    Flux<WorkoutPlan> findByExerciseTemplateId(String exerciseTemplateId);

    // Find workouts by estimated duration range
    Flux<WorkoutPlan> findByEstimatedDurationMinutesBetween(Integer minDuration, Integer maxDuration);

    // Find by folder ID
    Flux<WorkoutPlan> findByFolderId(String folderId);

    Flux<WorkoutPlan> findByFolderIdAndIsPublicTrue(String folderId);

    Flux<WorkoutPlan> findByFolderIdAndIsPublicFalse(String folderId);


    @Query(value = "{ 'folder_id': ?0 }", exists = true)
    Mono<Boolean> existsByFolderId(String folderId);

    // Find user's plans including public ones they created
    Flux<WorkoutPlan> findByCreatedByOrderByCreatedAtDesc(Long userId);

    // Check if user owns a plan
    Mono<Boolean> existsByIdAndCreatedBy(String id, Long userId);
}