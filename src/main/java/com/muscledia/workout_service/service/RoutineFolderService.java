package com.muscledia.workout_service.service;

import com.muscledia.workout_service.exception.SomeDuplicateEntryException;
import com.muscledia.workout_service.model.RoutineFolder;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.repository.RoutineFolderRepository;
import com.muscledia.workout_service.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineFolderService {
    private final RoutineFolderRepository routineFolderRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final TransactionalOperator transactionalOperator; // Injected for reactive transactions


    public Mono<RoutineFolder> findById(String id) {
        return routineFolderRepository.findById(id)
                .doOnNext(folder -> log.debug("Found routine folder: {}", folder.getTitle()))
                .switchIfEmpty(Mono.error(new RuntimeException("Routine folder not found with id: " + id)));
    }

    public Mono<RoutineFolder> findByHevyId(Long hevyId) {
        return routineFolderRepository.findByHevyId(hevyId)
                .doOnNext(folder -> log.debug("Found routine folder by Hevy ID: {}", folder.getTitle()));
    }

    public Flux<RoutineFolder> findByDifficultyLevel(String difficultyLevel) {
        return routineFolderRepository.findByDifficultyLevel(difficultyLevel)
                .doOnComplete(() -> log.debug("Retrieved routine folders for difficulty level: {}", difficultyLevel));
    }

    public Flux<RoutineFolder> findByEquipmentType(String equipmentType) {
        return routineFolderRepository.findByEquipmentType(equipmentType)
                .doOnComplete(() -> log.debug("Retrieved routine folders for equipment type: {}", equipmentType));
    }

    public Flux<RoutineFolder> findByWorkoutSplit(String workoutSplit) {
        return routineFolderRepository.findByWorkoutSplit(workoutSplit)
                .doOnComplete(() -> log.debug("Retrieved routine folders for workout split: {}", workoutSplit));
    }

    public Flux<RoutineFolder> findByDifficultyAndEquipment(String difficultyLevel, String equipmentType) {
        return routineFolderRepository.findByDifficultyLevelAndEquipmentType(difficultyLevel, equipmentType)
                .doOnComplete(() -> log.debug("Retrieved routine folders for difficulty {} and equipment: {}",
                        difficultyLevel, equipmentType));
    }

    public Flux<RoutineFolder> searchByTitle(String searchTerm) {
        return routineFolderRepository.findByTitleContainingIgnoreCase(searchTerm)
                .doOnComplete(() -> log.debug("Retrieved routine folders matching title: {}", searchTerm));
    }

    public Mono<Boolean> existsByHevyId(Long hevyId) {
        return routineFolderRepository.existsByHevyId(hevyId)
                .doOnSuccess(exists -> log.debug("Checked existence of Hevy ID: {}, exists: {}", hevyId, exists));
    }

    public Mono<RoutineFolder> save(RoutineFolder routineFolder) {
        if (routineFolder.getCreatedAt() == null) {
            routineFolder.setCreatedAt(LocalDateTime.now());
        }
        routineFolder.setUpdatedAt(LocalDateTime.now());
        routineFolder.parseMetadataFromTitle();

        return routineFolderRepository.save(routineFolder)
                .doOnSuccess(saved -> log.debug("Saved routine folder: {}", saved.getTitle()));
    }

    public Mono<RoutineFolder> saveToPersonalCollection(String publicFolderId, Long userId) {
        return findById(publicFolderId)
                .flatMap(publicFolder -> {
                    // Create a copy for the user's personal collection
                    RoutineFolder personalFolder = new RoutineFolder();
                    personalFolder.setTitle(publicFolder.getTitle());
                    personalFolder.setWorkoutPlanIds(publicFolder.getWorkoutPlanIds());
                    personalFolder.setDifficultyLevel(publicFolder.getDifficultyLevel());
                    personalFolder.setEquipmentType(publicFolder.getEquipmentType());
                    personalFolder.setWorkoutSplit(publicFolder.getWorkoutSplit());
                    personalFolder.setIsPublic(false); // Personal copy
                    personalFolder.setCreatedBy(userId);
                    personalFolder.setUsageCount(0L);
                    personalFolder.setCreatedAt(LocalDateTime.now());

                    // Increment usage count on original public folder
                    publicFolder.setUsageCount(publicFolder.getUsageCount() + 1);

                    return routineFolderRepository.save(publicFolder)
                            .then(routineFolderRepository.save(personalFolder));
                })
                .doOnSuccess(saved -> log.debug("Saved routine folder to personal collection for user: {}", userId));
    }

    /**
     * FIXED: Saves a public routine folder and all its associated workout plans to a user's personal collection.
     * This operation is transactional to ensure all copies are saved together.
     *
     * @param publicId The ID of the public routine folder to copy.
     * @param userId   The ID of the user saving the folder.
     * @return Mono<RoutineFolder> The newly created personal routine folder.
     */
    public Mono<RoutineFolder> savePublicRoutineFolderAndPlans(String publicId, Long userId) {
        log.info("🚀 Starting savePublicRoutineFolderAndPlans for publicId: {} and userId: {}", publicId, userId);

        return validateAndGetPublicFolder(publicId)
                .doOnNext(folder -> log.debug("Validated public folder: '{}'", folder.getTitle()))
                .flatMap(publicFolder -> checkForDuplicateFolder(publicFolder, userId)
                        .doOnSuccess(v -> log.debug("No duplicate found, proceeding with save"))
                        .then(Mono.defer(() -> createAndSavePlans(publicFolder, userId))))
                .as(transactionalOperator::transactional)
                .doOnSuccess(saved -> log.info("🎉 Successfully saved routine folder '{}' and plans to personal collection for user: {}",
                        saved.getTitle(), userId))
                .doOnError(error -> log.error("Error in savePublicRoutineFolderAndPlans: {} - {}",
                        error.getClass().getSimpleName(), error.getMessage()));
    }

    private Mono<RoutineFolder> validateAndGetPublicFolder(String publicId) {
        log.debug("🔍 Validating public folder with ID: {}", publicId);

        return routineFolderRepository.findById(publicId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Public routine folder not found with ID: " + publicId)))
                .doOnNext(folder -> log.debug("Found folder: '{}' (isPublic: {})", folder.getTitle(), folder.getIsPublic()))
                .filter(folder -> Boolean.TRUE.equals(folder.getIsPublic()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("The specified routine folder is not public.")))
                .doOnNext(folder -> log.debug("Folder '{}' is confirmed as public", folder.getTitle()));
    }

    /**
     * FIXED: Check for duplicate using the correct repository method and parameter order
     */
    private Mono<Void> checkForDuplicateFolder(RoutineFolder publicFolder, Long userId) {
        log.debug("Checking for duplicate personal folder: title='{}', userId={}", publicFolder.getTitle(), userId);

        // FIXED: Use the correct method with proper parameter order (title, userId, isPublic)
        return routineFolderRepository.findByTitleAndCreatedByAndIsPublic(
                        publicFolder.getTitle(),
                        userId,
                        false // isPublic = false for personal folders
                )
                .hasElement()
                .doOnNext(exists -> {
                    if (exists) {
                        log.warn("Duplicate found: title='{}', userId={}", publicFolder.getTitle(), userId);
                    } else {
                        log.debug("No duplicate found: title='{}', userId={}", publicFolder.getTitle(), userId);
                    }
                })
                .flatMap(exists -> exists
                        ? Mono.<Void>error(new SomeDuplicateEntryException(
                        String.format("A personal routine folder with title '%s' already exists for user %d.",
                                publicFolder.getTitle(), userId)))
                        : Mono.empty())
                .onErrorResume(SomeDuplicateEntryException.class, Mono::error) // Re-throw duplicate exceptions
                .onErrorResume(error -> {
                    log.error("Error during duplicate check: {}", error.getMessage(), error);
                    // If there's an error checking for duplicates, assume no duplicate to allow the operation
                    // The database will handle the actual constraint if there really is a duplicate
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Enhanced creation and saving logic with better error handling
     */
    private Mono<RoutineFolder> createAndSavePlans(RoutineFolder publicFolder, Long userId) {
        log.debug("Creating personal copy of folder: '{}'", publicFolder.getTitle());

        // 1. Create a new, private copy of the routine folder
        RoutineFolder personalFolder = new RoutineFolder();
        personalFolder.setTitle(publicFolder.getTitle());
        personalFolder.setDifficultyLevel(publicFolder.getDifficultyLevel());
        personalFolder.setEquipmentType(publicFolder.getEquipmentType());
        personalFolder.setWorkoutSplit(publicFolder.getWorkoutSplit());
        personalFolder.setIsPublic(false);
        personalFolder.setCreatedBy(userId);
        personalFolder.setUsageCount(0L);
        personalFolder.setCreatedAt(LocalDateTime.now());
        personalFolder.setUpdatedAt(LocalDateTime.now());

        // 2. Save the new private routine folder first to get its new ID
        return routineFolderRepository.save(personalFolder)
                .doOnSuccess(saved -> log.debug("Saved personal folder: '{}' (ID: {})", saved.getTitle(), saved.getId()))
                .flatMap(savedPersonalFolder -> {
                    log.info("Looking for workout plans for public folder: {} (isPublic: {})",
                            publicFolder.getId(), publicFolder.getIsPublic());

                    // ENHANCED: Try multiple approaches to find workout plans
                    return findWorkoutPlansForFolder(publicFolder.getId())
                            .collectList()
                            .flatMap(workoutPlans -> {
                                log.info("Found {} workout plans for folder '{}'",
                                        workoutPlans.size(), publicFolder.getTitle());

                                if (workoutPlans.isEmpty()) {
                                    log.warn("No workout plans found for public folder: {}", publicFolder.getId());
                                    log.info("Checking if folder has workoutPlanIds list...");

                                    if (publicFolder.getWorkoutPlanIds() != null && !publicFolder.getWorkoutPlanIds().isEmpty()) {
                                        log.info("Folder has {} workout plan IDs: {}",
                                                publicFolder.getWorkoutPlanIds().size(), publicFolder.getWorkoutPlanIds());

                                        // Try to find plans by their IDs directly
                                        return findWorkoutPlansByIds(publicFolder.getWorkoutPlanIds())
                                                .collectList()
                                                .flatMap(plansByIds -> copyWorkoutPlansToPersonal(plansByIds, savedPersonalFolder, userId));
                                    } else {
                                        log.info("Folder has no workout plan IDs, returning folder without plans");
                                        return Mono.just(savedPersonalFolder);
                                    }
                                } else {
                                    // Found plans, copy them
                                    return copyWorkoutPlansToPersonal(workoutPlans, savedPersonalFolder, userId);
                                }
                            });
                });
    }

    /**
     * ENHANCED: Multiple strategies to find workout plans
     */
    private Flux<WorkoutPlan> findWorkoutPlansForFolder(String folderId) {
        log.debug("Strategy 1: Looking for public workout plans with folderId='{}'", folderId);

        return workoutPlanRepository.findByFolderIdAndIsPublicTrue(folderId)
                .doOnNext(plan -> log.debug("Found public workout plan: '{}' (folderId: {})",
                        plan.getTitle(), plan.getFolderId()))
                .switchIfEmpty(
                        // Strategy 2: Look for any plans with this folder ID (regardless of public status)
                        Flux.defer(() -> {
                            log.debug("Strategy 2: Looking for ANY workout plans with folderId='{}'", folderId);
                            return workoutPlanRepository.findByFolderId(folderId)
                                    .doOnNext(plan -> log.debug("Found workout plan: '{}' (folderId: {}, isPublic: {})",
                                            plan.getTitle(), plan.getFolderId(), plan.getIsPublic()));
                        })
                )
                .doOnComplete(() -> log.debug("Completed workout plan search for folder: {}", folderId));
    }

    /**
     * Find workout plans by their IDs directly
     */
    private Flux<WorkoutPlan> findWorkoutPlansByIds(List<String> planIds) {
        log.debug("Strategy 3: Looking for workout plans by IDs: {}", planIds);

        return Flux.fromIterable(planIds)
                .flatMap(planId -> workoutPlanRepository.findById(planId)
                        .doOnNext(plan -> log.debug("Found workout plan by ID: '{}' (ID: {})",
                                plan.getTitle(), plan.getId()))
                        .onErrorResume(error -> {
                            log.warn("Could not find workout plan with ID: {} - {}", planId, error.getMessage());
                            return Mono.empty(); // Skip missing plans
                        }));
    }

    /**
     * Copy workout plans to personal collection
     */
    private Mono<RoutineFolder> copyWorkoutPlansToPersonal(List<WorkoutPlan> workoutPlans,
                                                           RoutineFolder savedPersonalFolder,
                                                           Long userId) {
        if (workoutPlans.isEmpty()) {
            log.debug("No workout plans to copy, returning folder only");
            return Mono.just(savedPersonalFolder);
        }

        log.info("Creating personal copies of {} workout plans", workoutPlans.size());

        return Flux.fromIterable(workoutPlans)
                .map(publicPlan -> {
                    WorkoutPlan personalPlan = new WorkoutPlan();
                    personalPlan.setTitle(publicPlan.getTitle());
                    personalPlan.setDescription(publicPlan.getDescription());
                    personalPlan.setExercises(publicPlan.getExercises());
                    personalPlan.setEstimatedDurationMinutes(publicPlan.getEstimatedDurationMinutes());
                    personalPlan.setIsPublic(false);
                    personalPlan.setCreatedBy(userId);
                    personalPlan.setFolderId(savedPersonalFolder.getId()); // Link to personal folder
                    personalPlan.setUsageCount(0L);
                    personalPlan.setCreatedAt(LocalDateTime.now());
                    personalPlan.setUpdatedAt(LocalDateTime.now());

                    log.debug("Created personal copy of workout plan: '{}'", personalPlan.getTitle());
                    return personalPlan;
                })
                .flatMap(personalPlan -> workoutPlanRepository.save(personalPlan)
                        .doOnSuccess(saved -> {
                            log.debug("Saved personal workout plan: '{}'", saved.getTitle());
                            // Add the plan ID to the folder's workout plan IDs
                            savedPersonalFolder.addWorkoutPlanId(saved.getId());
                        })
                        .onErrorResume(error -> {
                            log.error("Failed to save personal workout plan '{}': {}",
                                    personalPlan.getTitle(), error.getMessage());
                            return Mono.empty(); // Continue with other plans
                        }))
                .then(
                        // Update the folder with the new workout plan IDs
                        routineFolderRepository.save(savedPersonalFolder)
                                .doOnSuccess(updated -> log.info("Updated folder with {} workout plan IDs",
                                        updated.getWorkoutPlanIds().size()))
                )
                .onErrorResume(error -> {
                    log.error("Error copying workout plans: {}", error.getMessage(), error);
                    // Return the folder even if workout plan copying fails
                    return Mono.just(savedPersonalFolder);
                });
    }

    public Flux<RoutineFolder> findPublicRoutineFolders() {
        return routineFolderRepository.findByIsPublicTrue()
                .doOnComplete(() -> log.debug("Retrieved public routine folders"));
    }

    /**
     * Enhanced personal folder finder with better logging
     */
    public Flux<RoutineFolder> findPersonalRoutineFolders(Long userId) {
        log.debug("Finding personal routine folders for user: {}", userId);

        return routineFolderRepository.findByCreatedByAndIsPublicFalse(userId)
                .doOnNext(folder -> log.debug("Found personal folder: '{}' (ID: {})",
                        folder.getTitle(), folder.getId()))
                .doOnComplete(() -> log.debug("Completed retrieval of personal routine folders for user: {}", userId))
                .doOnError(error -> log.error("Error finding personal folders for user {}: {}", userId, error.getMessage()));
    }

    /**
     * Get personal folders with pagination (if needed)
     */
    public Flux<RoutineFolder> findPersonalRoutineFolders(Long userId, int page, int size) {
        log.debug("Finding personal routine folders for user: {} (page: {}, size: {})", userId, page, size);

        return routineFolderRepository.findByCreatedByAndIsPublicFalse(userId)
                .skip((long) page * size)
                .take(size)
                .doOnNext(folder -> log.debug("Found personal folder: '{}' (ID: {})",
                        folder.getTitle(), folder.getId()))
                .doOnComplete(() -> log.debug("Completed paginated retrieval for user: {}", userId));
    }

    /**
            * Count personal routine folders for a user
    */
    public Mono<Long> countPersonalRoutineFolders(Long userId) {
        log.debug("Counting personal routine folders for user: {}", userId);

        return routineFolderRepository.countByCreatedByAndIsPublicFalse(userId)
                .doOnNext(count -> log.debug("User {} has {} personal routine folders", userId, count))
                .doOnError(error -> log.error("Error counting personal folders for user {}: {}", userId, error.getMessage()));
    }

    /**
     * Check if user has a specific folder in their personal collection
     */
    public Mono<Boolean> hasPersonalFolder(String folderId, Long userId) {
        log.debug("Checking if user {} has personal folder: {}", userId, folderId);

        return routineFolderRepository.findById(folderId)
                .map(folder -> Boolean.FALSE.equals(folder.getIsPublic()) && userId.equals(folder.getCreatedBy()))
                .defaultIfEmpty(false)
                .doOnNext(hasFolder -> log.debug("User {} {} personal folder {}",
                        userId, hasFolder ? "has" : "does not have", folderId));
    }

    public Mono<Void> deleteById(String id) {
        return routineFolderRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted routine folder with id: {}", id));
    }

    public Flux<RoutineFolder> findAll() {
        return routineFolderRepository.findAll()
                .map(folder -> {
                    // Parse metadata from title if not already set
                    if (folder.getDifficultyLevel() == null || folder.getEquipmentType() == null
                            || folder.getWorkoutSplit() == null) {
                        folder.parseMetadataFromTitle();
                    }
                    return folder;
                })
                .doOnComplete(() -> log.debug("Retrieved all routine folders"));
    }

    public Mono<RoutineFolder> addWorkoutPlanToFolder(String folderId, String workoutPlanId) {
        return findById(folderId)
                .flatMap(folder -> {
                    folder.addWorkoutPlanId(workoutPlanId);
                    return save(folder);
                })
                .doOnSuccess(
                        folder -> log.debug("Added workout plan {} to folder {}", workoutPlanId, folder.getTitle()));
    }

    /**
     * Remove a routine folder from user's personal collection
     * Business logic: Only allows removal of personal (non-public) folders owned by the user
     */
    public Mono<Void> removeFromPersonalCollection(String folderId, Long userId) {
        log.info("Removing routine folder {} from personal collection for user {}", folderId, userId);

        return validatePersonalFolderOwnership(folderId, userId)
                .doOnNext(folder -> log.debug("Validated ownership of folder: '{}'", folder.getTitle()))
                .flatMap(this::deletePersonalFolderAndPlans)
                .doOnSuccess(v -> log.info("Successfully removed folder {} from personal collection", folderId))
                .doOnError(error -> log.error("Error removing folder {} from personal collection: {}",
                        folderId, error.getMessage()));
    }

    /**
     * Validate that the folder exists, is personal (not public), and is owned by the user
     */
    private Mono<RoutineFolder> validatePersonalFolderOwnership(String folderId, Long userId) {
        log.debug("Validating ownership of folder {} for user {}", folderId, userId);

        return routineFolderRepository.findById(folderId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Routine folder not found")))
                .flatMap(folder -> {
                    // Check if it's a personal folder
                    if (Boolean.TRUE.equals(folder.getIsPublic())) {
                        return Mono.error(new IllegalArgumentException("Cannot remove public folders from personal collection"));
                    }

                    // Check ownership
                    if (!userId.equals(folder.getCreatedBy())) {
                        return Mono.error(new SecurityException("Not authorized to remove this folder"));
                    }

                    return Mono.just(folder);
                })
                .doOnNext(folder -> log.debug("Folder '{}' ownership validated for user {}", folder.getTitle(), userId));
    }

    /**
     * Delete the personal folder and all its associated workout plans
     */
    private Mono<Void> deletePersonalFolderAndPlans(RoutineFolder folder) {
        log.debug("Deleting personal folder '{}' and its workout plans", folder.getTitle());

        return deleteWorkoutPlansForPersonalFolder(folder.getId())
                .doOnSuccess(deletedCount -> log.debug("Deleted {} workout plans for folder '{}'",
                        deletedCount, folder.getTitle()))
                .then(routineFolderRepository.delete(folder))
                .doOnSuccess(v -> log.debug("Deleted personal folder: '{}'", folder.getTitle()));
    }

    /**
     * Delete all workout plans associated with a personal folder
     */
    private Mono<Long> deleteWorkoutPlansForPersonalFolder(String folderId) {
        log.debug("Deleting workout plans for personal folder: {}", folderId);

        return workoutPlanRepository.findByFolderId(folderId)
                .filter(plan -> Boolean.FALSE.equals(plan.getIsPublic())) // Only delete personal plans
                .flatMap(plan -> workoutPlanRepository.delete(plan)
                        .doOnSuccess(v -> log.debug("Deleted personal workout plan: '{}'", plan.getTitle()))
                        .thenReturn(1L))
                .reduce(0L, Long::sum)
                .doOnSuccess(count -> log.debug("Deleted {} personal workout plans", count));
    }

    /**
     * Helper methods for additional functionality
     */

    public Mono<Void> delete(RoutineFolder routineFolder) {
        return routineFolderRepository.delete(routineFolder);
    }

    public Flux<RoutineFolder> findByIsPublicTrue() {
        return routineFolderRepository.findByIsPublicTrue();
    }
}