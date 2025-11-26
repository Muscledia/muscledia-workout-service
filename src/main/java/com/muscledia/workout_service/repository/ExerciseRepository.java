package com.muscledia.workout_service.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;

@Repository
public interface ExerciseRepository extends ReactiveMongoRepository<Exercise, String> {
    Mono<Exercise> findByExternalApiId(String externalApiId);

    // Add custom queries as needed, e.g., find by difficulty, equipment,
    // target_muscle
    Flux<Exercise> findByDifficulty(ExerciseDifficulty difficulty);

    Flux<Exercise> findByEquipment(String equipment);

    Flux<Exercise> findByTargetMuscle(String targetMuscle);

    // Search by name (case-insensitive, partial match)
    Flux<Exercise> findByNameContainingIgnoreCase(String name);

    // Find exercises by multiple equipment types
    @Query("{'equipment': {'$in': ?0}}")
    Flux<Exercise> findByEquipmentIn(List<String> equipmentList);

    // Find exercises targeting specific muscle groups
    @Query("{'muscleGroups.name': ?0, 'muscleGroups.isPrimary': true}")
    Flux<Exercise> findByPrimaryMuscleGroup(String muscleName);

    // Find exercises by difficulty with pagination
    Flux<Exercise> findByDifficulty(ExerciseDifficulty difficulty, Pageable pageable);

    // Search exercises by name and difficulty
    Flux<Exercise> findByNameContainingIgnoreCaseAndDifficulty(String name, ExerciseDifficulty difficulty);

    // Find exercises that don't require equipment
    @Query("{'equipment': {'$exists': false}}")
    Flux<Exercise> findBodyweightExercises();

    // Find exercises by multiple target muscles
    @Query("{'targetMuscle': {'$in': ?0}}")
    Flux<Exercise> findByTargetMuscleIn(List<String> targetMuscles);

    // Count exercises by difficulty
    Mono<Long> countByDifficulty(ExerciseDifficulty difficulty);

    // Find exercises that have animation URLs
    Flux<Exercise> findByAnimationUrlIsNotNull();

    // Complex search with multiple criteria
    @Query("{'difficulty': ?0, '$or': [{'targetMuscle': ?1}, {'muscleGroups.name': ?1}]}")
    Flux<Exercise> findByDifficultyAndMuscleInvolved(ExerciseDifficulty difficulty, String muscle);

    // ==================== NEW METHODS FOR WORKOUT PLAN CREATION ====================

    /**
     * Find exercises by muscle group (checks both primary and secondary muscle groups)
     * and equipment with pagination
     */
    @Query("{ 'muscleGroups.name': ?0, 'equipment': ?1 }")
    Flux<Exercise> findByMuscleGroupAndEquipment(String muscleGroup, String equipment, Pageable pageable);

    /**
     * Find exercises by muscle group with pagination
     * Checks both primary and secondary muscle groups
     */
    @Query("{ 'muscleGroups.name': ?0 }")
    Flux<Exercise> findByMuscleGroupPaginated(String muscleGroup, Pageable pageable);

    /**
     * Find exercises by equipment with pagination
     */
    Flux<Exercise> findByEquipment(String equipment, Pageable pageable);

    /**
     * Find all exercises with pagination (for browsing)
     */
    Flux<Exercise> findAllBy(Pageable pageable);
}
