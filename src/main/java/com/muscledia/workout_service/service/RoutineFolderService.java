package com.muscledia.workout_service.service;

import com.muscledia.workout_service.exception.SomeDuplicateEntryException;
import com.muscledia.workout_service.exception.SomeEntityNotFoundException;
import com.muscledia.workout_service.exception.SomeInvalidRequestException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineFolderService {
    private final RoutineFolderRepository routineFolderRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final AuthenticationService authenticationService;
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


    /**
     * FIXED: Saves a public routine folder and all its associated workout plans to a user's personal collection.
     * Operation is Idempotent: If it already exists, returns the existing copy instead of erroring.
     *
     * @param publicId The ID of the public routine folder to copy.
     * @param userId   The ID of the user saving the folder.
     * @return Mono<RoutineFolder> The new or existing personal routine folder.
     */
    public Mono<RoutineFolder> savePublicRoutineFolderAndPlans(String publicId, Long userId) {
        log.info("🚀 Starting savePublicRoutineFolderAndPlans for publicId: {} and userId: {}", publicId, userId);

        return validateAndGetPublicFolder(publicId)
                .flatMap(publicFolder -> findExistingPersonalFolder(publicFolder, userId)
                        .flatMap(existing -> {
                            log.info("♻️ Routine folder '{}' already exists in collection. Returning existing copy.", existing.getTitle());
                            return Mono.just(existing);
                        })
                        .switchIfEmpty(
                                Mono.defer(() -> {
                                    log.info("🆕 No duplicate found. Creating new copy for '{}'", publicFolder.getTitle());
                                    return createAndSavePlans(publicFolder, userId);
                                })
                        )
                )
                .as(transactionalOperator::transactional)
                .doOnSuccess(saved -> log.info("🎉 Operation complete for routine folder '{}' (User: {})", saved.getTitle(), userId))
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
     * Find routine folder by ID with optional workout plans
     * ALWAYS returns Map for consistency - workoutPlans field is empty list if not requested
     */
    public Mono<Map<String, Object>> findByIdWithWorkoutPlans(String id) {
        return findById(id)
                .flatMap(folder -> {
                    if (folder.getWorkoutPlanIds() == null || folder.getWorkoutPlanIds().isEmpty()) {
                        return Mono.just(createFolderResponse(folder, List.of()));
                    }

                    return Flux.fromIterable(folder.getWorkoutPlanIds())
                            .flatMap(planId -> workoutPlanRepository.findById(planId)
                                    .onErrorResume(error -> {
                                        log.warn("Skipping missing plan {}: {}", planId, error.getMessage());
                                        return Mono.empty();
                                    }))
                            .collectList()
                            .map(plans -> createFolderResponse(folder, plans));
                });
    }

    /**
     * Check if a folder with the same title already exists in user's personal collection
     * Returns the existing folder if found, or empty if not.
     */
    private Mono<RoutineFolder> findExistingPersonalFolder(RoutineFolder publicFolder, Long userId) {
        // Since we generate new Hevy IDs for copies, we mainly check by Title
        // Note: You might want to refine this if you allow users to have two folders with the same name

        return routineFolderRepository.findByTitleAndCreatedByAndIsPublic(
                publicFolder.getTitle(),
                userId,
                false
        ); // Get the first match if any
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

        // CRITICAL FIX: ALWAYS generate a unique ID to avoid null conflicts
        // Don't ever set hevyId to null for personal folders
        personalFolder.setHevyId(generateUniqueInternalId());

        log.debug("Generated unique hevyId for personal folder: {}", personalFolder.getHevyId());

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

    // Helper method to generate unique internal ID
    private Long generateUniqueInternalId() {
        // Generate a unique Long ID for internal use
        // This could be a timestamp-based ID or any other unique generation strategy
        return System.currentTimeMillis() + (long)(Math.random() * 1000);
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
     * Update personal routine folder
     * Business logic: Validates ownership and allows updates to title, description, metadata
     */
    public Mono<RoutineFolder> updatePersonalRoutineFolder(
            String folderId,
            Long userId,
            Map<String, Object> updates
    ) {
        log.debug("Updating personal routine folder {} for user {}", folderId, userId);

        return validatePersonalFolderOwnership(folderId, userId)
                .flatMap(folder -> {
                    // Apply updates
                    if (updates.containsKey("title")) {
                        folder.setTitle((String) updates.get("title"));
                    }
                    if (updates.containsKey("difficultyLevel")) {
                        folder.setDifficultyLevel((String) updates.get("difficultyLevel"));
                    }
                    if (updates.containsKey("equipmentType")) {
                        folder.setEquipmentType((String) updates.get("equipmentType"));
                    }
                    if (updates.containsKey("workoutSplit")) {
                        folder.setWorkoutSplit((String) updates.get("workoutSplit"));
                    }

                    folder.setUpdatedAt(LocalDateTime.now());

                    return routineFolderRepository.save(folder);
                })
                .doOnSuccess(saved -> log.info("Updated routine folder: '{}'", saved.getTitle()))
                .doOnError(error -> log.error("Error updating routine folder: {}", error.getMessage()));
    }

    /**
     * Add workout plan to personal routine folder
     * Business logic: Validates ownership, checks plan exists, adds plan ID to folder
     */
    public Mono<RoutineFolder> addWorkoutPlanToPersonalFolder(
            String folderId,
            String planId,
            Long userId
    ) {
        log.debug("Adding workout plan {} to folder {} for user {}", planId, folderId, userId);

        return validatePersonalFolderOwnership(folderId, userId)
                .zipWith(validateWorkoutPlanExists(planId))
                .flatMap(tuple -> {
                    RoutineFolder folder = tuple.getT1();

                    // Check if plan already in folder
                    if (folder.getWorkoutPlanIds().contains(planId)) {
                        return Mono.error(new IllegalArgumentException(
                                "Workout plan already exists in this routine folder"
                        ));
                    }

                    folder.addWorkoutPlanId(planId);
                    folder.setUpdatedAt(LocalDateTime.now());

                    return routineFolderRepository.save(folder);
                })
                .doOnSuccess(saved -> log.info("Added workout plan to folder: '{}'", saved.getTitle()))
                .doOnError(error -> log.error("Error adding workout plan: {}", error.getMessage()));
    }

    /**
     * Remove workout plan from personal routine folder
     * Business logic: Validates ownership, removes plan ID from folder
     */
    public Mono<RoutineFolder> removeWorkoutPlanFromPersonalFolder(
            String folderId,
            String planId,
            Long userId
    ) {
        log.debug("Removing workout plan {} from folder {} for user {}", planId, folderId, userId);

        return validatePersonalFolderOwnership(folderId, userId)
                .flatMap(folder -> {
                    if (!folder.getWorkoutPlanIds().contains(planId)) {
                        return Mono.error(new IllegalArgumentException(
                                "Workout plan not found in this routine folder"
                        ));
                    }

                    folder.removeWorkoutPlanId(planId);
                    folder.setUpdatedAt(LocalDateTime.now());

                    return routineFolderRepository.save(folder);
                })
                .doOnSuccess(saved -> log.info("Removed workout plan from folder: '{}'", saved.getTitle()))
                .doOnError(error -> log.error("Error removing workout plan: {}", error.getMessage()));
    }

    private Mono<Void> copyPlannedExercises(String sourcePlanId, String targetPlanId) {
        log.debug("Copying planned exercises from plan {} to plan {}", sourcePlanId, targetPlanId);

        return workoutPlanRepository.findById(sourcePlanId)
                .flatMap(sourcePlan -> {
                    if (sourcePlan.getExercises() == null || sourcePlan.getExercises().isEmpty()) {
                        return Mono.empty();
                    }

                    return workoutPlanRepository.findById(targetPlanId)
                            .flatMap(targetPlan -> {
                                targetPlan.setExercises(sourcePlan.getExercises());
                                return workoutPlanRepository.save(targetPlan).then();
                            });
                })
                .onErrorResume(error -> {
                    log.warn("Error copying exercises: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Validate that a workout plan exists
     */
    private Mono<WorkoutPlan> validateWorkoutPlanExists(String planId) {
        return workoutPlanRepository.findById(planId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Workout plan not found")))
                .doOnNext(plan -> log.debug("Validated workout plan exists: '{}'", plan.getTitle()));
    }

    /**
     * Helper methods for additional functionality
     */


    /**
     * Helper to create response map with folder + workout plans
     */
    public Map<String, Object> createFolderResponse(RoutineFolder folder, List<WorkoutPlan> workoutPlans) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", folder.getId());
        response.put("hevyId", folder.getHevyId());
        response.put("folderIndex", folder.getFolderIndex());
        response.put("title", folder.getTitle());
        response.put("difficultyLevel", folder.getDifficultyLevel());
        response.put("equipmentType", folder.getEquipmentType());
        response.put("workoutSplit", folder.getWorkoutSplit());
        response.put("isPublic", folder.getIsPublic());
        response.put("createdBy", folder.getCreatedBy());
        response.put("usageCount", folder.getUsageCount());
        response.put("createdAt", folder.getCreatedAt());
        response.put("updatedAt", folder.getUpdatedAt());
        response.put("workoutPlanIds", folder.getWorkoutPlanIds());
        response.put("workoutPlans", workoutPlans);  // Empty list or populated
        response.put("workoutPlanCount", workoutPlans.size());
        response.put("personal", Boolean.FALSE.equals(folder.getIsPublic()));

        return response;
    }

    public Mono<Void> delete(RoutineFolder routineFolder) {
        return routineFolderRepository.delete(routineFolder);
    }

    public Flux<RoutineFolder> findByIsPublicTrue() {
        return routineFolderRepository.findByIsPublicTrue();
    }
}