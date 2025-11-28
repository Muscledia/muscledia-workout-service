package com.muscledia.workout_service.repository;

import java.util.List;

import com.muscledia.workout_service.model.enums.ExerciseCategory;
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
    // ==================== BASIC FINDERS ====================

    // FIXED: Changed from findByExternalApiId to findByExternalId
    Mono<Exercise> findByExternalId(String externalId);

    // ==================== SINGLE FIELD FILTERS ====================

    Flux<Exercise> findByDifficulty(ExerciseDifficulty difficulty);

    Flux<Exercise> findByDifficulty(ExerciseDifficulty difficulty, Pageable pageable);

    Mono<Long> countByDifficulty(ExerciseDifficulty difficulty);

    Flux<Exercise> findByEquipment(String equipment);

    Flux<Exercise> findByEquipment(String equipment, Pageable pageable);

    Flux<Exercise> findByEquipmentIn(List<String> equipmentList);

    Flux<Exercise> findByTargetMuscle(String targetMuscle);

    Flux<Exercise> findByTargetMuscleIn(List<String> targetMuscles);

    /**
     * Find by exercise category
     * NEW: Supports category filtering in workout plan creation
     */
    Flux<Exercise> findByCategory(ExerciseCategory category);

    Flux<Exercise> findByCategory(ExerciseCategory category, Pageable pageable);

    /**
     * Find by body part
     * NEW: Supports body part filtering in workout plan creation
     */
    Flux<Exercise> findByBodyPart(String bodyPart);

    Flux<Exercise> findByBodyPart(String bodyPart, Pageable pageable);

    // ==================== SEARCH ====================

    Flux<Exercise> findByNameContainingIgnoreCase(String name);

    Flux<Exercise> findByNameContainingIgnoreCaseAndDifficulty(String name, ExerciseDifficulty difficulty);

    // ==================== COMPLEX QUERIES ====================

    /**
     * Find by primary OR secondary muscle groups
     * Checks if muscle is in targetMuscle OR in secondaryMuscles array
     */
    @Query("{'$or': [{'targetMuscle': ?0}, {'secondaryMuscles': ?0}]}")
    Flux<Exercise> findByPrimaryMuscleGroup(String muscleName);

    /**
     * Find by muscle group with pagination
     * NEW: Added pagination support for better performance
     */
    @Query("{'$or': [{'targetMuscle': ?0}, {'secondaryMuscles': ?0}]}")
    Flux<Exercise> findByMuscleGroupPaginated(String muscleGroup, Pageable pageable);

    /**
     * Find by difficulty and muscle involvement
     */
    @Query("{'difficulty': ?0, '$or': [{'targetMuscle': ?1}, {'secondaryMuscles': ?1}]}")
    Flux<Exercise> findByDifficultyAndMuscleInvolved(ExerciseDifficulty difficulty, String muscle);

    /**
     * Find by muscle group and equipment with pagination
     * NEW: Supports combined filtering in workout plan creation
     */
    @Query("{'$or': [{'targetMuscle': ?0}, {'secondaryMuscles': ?0}], 'equipment': ?1}")
    Flux<Exercise> findByMuscleGroupAndEquipment(String muscleGroup, String equipment, Pageable pageable);

    /**
     * Find by category and difficulty
     * NEW: Supports advanced filtering combinations
     */
    Flux<Exercise> findByCategoryAndDifficulty(ExerciseCategory category, ExerciseDifficulty difficulty);

    Flux<Exercise> findByCategoryAndDifficulty(ExerciseCategory category, ExerciseDifficulty difficulty, Pageable pageable);

    /**
     * Find by body part and equipment
     * NEW: Supports equipment-specific body part training
     */
    Flux<Exercise> findByBodyPartAndEquipment(String bodyPart, String equipment);

    Flux<Exercise> findByBodyPartAndEquipment(String bodyPart, String equipment, Pageable pageable);

    /**
     * Find by category and body part
     * NEW: Supports category-specific body part filtering
     */
    Flux<Exercise> findByCategoryAndBodyPart(ExerciseCategory category, String bodyPart);

    Flux<Exercise> findByCategoryAndBodyPart(ExerciseCategory category, String bodyPart, Pageable pageable);

    // ==================== SPECIAL FILTERS ====================

    /**
     * Find bodyweight exercises (no equipment required)
     */
    @Query("{'equipment': {'$regex': '^body.*weight$', '$options': 'i'}}")
    Flux<Exercise> findBodyweightExercises();

    /**
     * Find exercises with images
     */
    Flux<Exercise> findByImageUrlIsNotNull();

    /**
     * Find all with pagination
     */
    Flux<Exercise> findAllBy(Pageable pageable);
}
