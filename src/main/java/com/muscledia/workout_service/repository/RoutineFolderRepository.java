package com.muscledia.workout_service.repository;

import com.muscledia.workout_service.model.RoutineFolder;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RoutineFolderRepository extends ReactiveMongoRepository<RoutineFolder, String> {
    /**
     * FIXED: Explicit query to ensure correct field mapping
     * This prevents the field name mismatch issue (created_by vs createdBy)
     */
    @Query("{ 'title': ?0, 'createdBy': ?1, 'isPublic': ?2 }")
    Mono<RoutineFolder> findByTitleAndCreatedByAndIsPublic(String title, Long createdBy, Boolean isPublic);

    /**
     * Find all public routine folders
     */
    @Query("{ 'isPublic': true }")
    Flux<RoutineFolder> findByIsPublicTrue();

    /**
     * Find personal folders for a specific user
     */
    @Query("{ 'createdBy': ?0, 'isPublic': false }")
    Flux<RoutineFolder> findByCreatedByAndIsPublicFalse(Long createdBy);

    /**
     * Find by Hevy ID (for imported folders)
     */
    @Query("{ 'hevyId': ?0 }")
    Mono<RoutineFolder> findByHevyId(Long hevyId);

    /**
     * Check if folder exists by Hevy ID
     */
    @Query(value = "{ 'hevyId': ?0 }", exists = true)
    Mono<Boolean> existsByHevyId(Long hevyId);

    /**
     * Find by difficulty level (public only)
     */
    @Query("{ 'difficultyLevel': ?0, 'isPublic': true }")
    Flux<RoutineFolder> findByDifficultyLevel(String difficultyLevel);

    /**
     * Find by equipment type (public only)
     */
    @Query("{ 'equipmentType': ?0, 'isPublic': true }")
    Flux<RoutineFolder> findByEquipmentType(String equipmentType);

    /**
     * Find by workout split (public only)
     */
    @Query("{ 'workoutSplit': ?0, 'isPublic': true }")
    Flux<RoutineFolder> findByWorkoutSplit(String workoutSplit);

    /**
     * Find by difficulty and equipment type (public only)
     */
    @Query("{ 'difficultyLevel': ?0, 'equipmentType': ?1, 'isPublic': true }")
    Flux<RoutineFolder> findByDifficultyLevelAndEquipmentType(String difficultyLevel, String equipmentType);

    /**
     * Search by title (case insensitive, public only)
     */
    @Query("{ 'title': { $regex: ?0, $options: 'i' }, 'isPublic': true }")
    Flux<RoutineFolder> findByTitleContainingIgnoreCase(String searchTerm);

    /**
     * Additional helper methods for debugging and testing
     */

    /**
     * Alternative method using proper field names for duplicate checking
     */
    @Query("{ 'title': ?0, 'createdBy': ?1, 'isPublic': false }")
    Flux<RoutineFolder> findPersonalFoldersByTitleAndUser(String title, Long createdBy);

    /**
     * Check if personal folder with same title exists for user
     */
    @Query(value = "{ 'title': ?0, 'createdBy': ?1, 'isPublic': false }", exists = true)
    Mono<Boolean> existsByTitleAndCreatedByAndIsPublicFalse(String title, Long createdBy);

    /**
     * Count personal folders for a user
     */
    @Query(value = "{ 'createdBy': ?0, 'isPublic': false }", count = true)
    Mono<Long> countPersonalFoldersByUser(Long createdBy);

    /**
     * Find folders by difficulty level (both public and personal)
     */
    @Query("{ 'difficultyLevel': ?0 }")
    Flux<RoutineFolder> findAllByDifficultyLevel(String difficultyLevel);

    /**
     * Find all folders for a specific user (both public they created and personal)
     */
    @Query("{ 'createdBy': ?0 }")
    Flux<RoutineFolder> findAllByCreatedBy(Long createdBy);

    Mono<Long> countByCreatedByAndIsPublicFalse(Long createdBy);
}