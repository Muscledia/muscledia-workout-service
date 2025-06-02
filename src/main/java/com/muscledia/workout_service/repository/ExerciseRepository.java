package com.muscledia.workout_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;

@Repository
public interface ExerciseRepository extends MongoRepository<Exercise, String> {
    Optional<Exercise> findByExternalApiId(String externalApiId);

    // Add custom queries as needed, e.g., find by difficulty, equipment,
    // target_muscle
    List<Exercise> findByDifficulty(ExerciseDifficulty difficulty);

    List<Exercise> findByEquipment(String equipment);

    List<Exercise> findByTargetMuscle(String targetMuscle);

    // Search by name (case-insensitive, partial match)
    List<Exercise> findByNameContainingIgnoreCase(String name);

    // Find exercises by multiple equipment types
    @Query("{'equipment': {'$in': ?0}}")
    List<Exercise> findByEquipmentIn(List<String> equipmentList);

    // Find exercises targeting specific muscle groups
    @Query("{'muscleGroups.name': ?0, 'muscleGroups.isPrimary': true}")
    List<Exercise> findByPrimaryMuscleGroup(String muscleName);

    // Find exercises by difficulty with pagination
    Page<Exercise> findByDifficulty(ExerciseDifficulty difficulty, Pageable pageable);

    // Search exercises by name and difficulty
    List<Exercise> findByNameContainingIgnoreCaseAndDifficulty(String name, ExerciseDifficulty difficulty);

    // Find exercises that don't require equipment
    @Query("{'equipment': {'$exists': false}}")
    List<Exercise> findBodyweightExercises();

    // Find exercises by multiple target muscles
    @Query("{'targetMuscle': {'$in': ?0}}")
    List<Exercise> findByTargetMuscleIn(List<String> targetMuscles);

    // Count exercises by difficulty
    long countByDifficulty(ExerciseDifficulty difficulty);

    // Find exercises that have animation URLs
    List<Exercise> findByAnimationUrlIsNotNull();

    // Complex search with multiple criteria
    @Query("{'difficulty': ?0, '$or': [{'targetMuscle': ?1}, {'muscleGroups.name': ?1}]}")
    List<Exercise> findByDifficultyAndMuscleInvolved(ExerciseDifficulty difficulty, String muscle);
}
