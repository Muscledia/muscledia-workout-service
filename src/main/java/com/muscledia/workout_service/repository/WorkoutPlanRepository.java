package com.muscledia.workout_service.repository;

import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.enums.WorkoutDifficulty;
import com.muscledia.workout_service.model.enums.WorkoutType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkoutPlanRepository extends ReactiveMongoRepository<WorkoutPlan, String> {
    // Find by name (case-insensitive, partial match)
    Flux<WorkoutPlan> findByNameContainingIgnoreCase(String name);

    // Find by type
    Flux<WorkoutPlan> findByType(WorkoutType type);

    // Find by difficulty
    Flux<WorkoutPlan> findByDifficulty(WorkoutDifficulty difficulty);

    // Find public workout plans
    Flux<WorkoutPlan> findByIsPublicTrue();

    // Find by creator
    Flux<WorkoutPlan> findByCreatedBy(Long userId);

    // Find by type and difficulty
    Flux<WorkoutPlan> findByTypeAndDifficulty(WorkoutType type, WorkoutDifficulty difficulty);

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

    // Search by name or tags
    @Query("{'isPublic': true, '$or': [{'name': {'$regex': ?0, '$options': 'i'}}, {'tags': {'$regex': ?0, '$options': 'i'}}]}")
    Flux<WorkoutPlan> searchPublicWorkouts(String searchTerm);

    // Find workouts created between dates
    Flux<WorkoutPlan> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Find workouts that contain specific exercises
    @Query("{'exercises.exerciseId': ?0}")
    Flux<WorkoutPlan> findByExerciseId(String exerciseId);

    // Count workouts by type
    Mono<Long> countByType(WorkoutType type);

    // Find workouts by multiple types
    Flux<WorkoutPlan> findByTypeIn(List<WorkoutType> types);

    // Find workouts by estimated duration range
    Flux<WorkoutPlan> findByEstimatedDurationMinutesBetween(Integer minDuration, Integer maxDuration);
}