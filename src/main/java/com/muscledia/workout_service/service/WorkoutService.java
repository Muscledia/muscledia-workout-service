package com.muscledia.workout_service.service;

import com.muscledia.workout_service.domain.service.WorkoutOrchestrator;
import com.muscledia.workout_service.domain.service.WorkoutValidationService;
import com.muscledia.workout_service.dto.request.StartWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.LogSetRequest;
import com.muscledia.workout_service.exception.ExerciseNotFoundException;
import com.muscledia.workout_service.exception.InvalidWorkoutStateException;
import com.muscledia.workout_service.exception.WorkoutNotFoundException;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.embedded.PlannedExercise;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import com.muscledia.workout_service.repository.WorkoutRepository;
import com.muscledia.workout_service.service.analytics.PersonalRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLEAN ARCHITECTURE WorkoutService
 * Following the same pattern as WorkoutOrchestrator for PersonalRecord events
 *
 * SOLID Principles:
 * - Single Responsibility: Set logging + PR detection delegation
 * - Open/Closed: Easy to extend with new PR features
 * - Liskov Substitution: PersonalRecordService abstraction
 * - Interface Segregation: Clean separation of concerns
 * - Dependency Inversion: Depends on PersonalRecordService abstraction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutService {
    // Core dependencies
    private final WorkoutRepository workoutRepository;
    private final WorkoutPlanService workoutPlanService;
    private final PersonalRecordService personalRecordService;

    // Domain Services for business logic
    private final WorkoutOrchestrator workoutOrchestrator;
    private final WorkoutValidationService workoutValidationService;

    // Feature flags for migration
    @Value("${workout.use-new-calculation:true}")
    private boolean useNewCalculation;

    @Value("${workout.use-orchestrator:true}") // Changed default to true
    private boolean useOrchestrator;

    // ==================== WORKOUT SESSION LIFECYCLE ====================

    /**
     * Start a new workout session - enhanced to support plan integration
     */
    public Mono<Workout> startWorkout(Long userId, StartWorkoutRequest request) {
        log.info("Starting new workout session for user {}: {}", userId, request.getWorkoutName());

        // Check if we should start from a workout plan
        if (request.getWorkoutPlanId() != null && request.isUseWorkoutPlan()) {
            log.info("Starting workout from plan: {}", request.getWorkoutPlanId());
            return startWorkoutFromPlan(userId, request);
        }

        // Original manual workout creation
        return createManualWorkout(userId, request);
    }

    /**
     * Create a manual workout (original behavior)
     */
    public Mono<Workout> createManualWorkout(Long userId, StartWorkoutRequest request) {
        log.info("Starting new workout session for user {}: {}", userId, request.getWorkoutName());

        Workout workout = Workout.builder()
                .userId(userId)
                .workoutName(request.getWorkoutName())
                .workoutPlanId(request.getWorkoutPlanId())
                .workoutType(request.getWorkoutType())
                .location(request.getLocation())
                .notes(request.getNotes())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .exercises(new ArrayList<>())
                .status(WorkoutStatus.IN_PROGRESS)
                .workoutDate(LocalDateTime.now())
                .build();

        // Use domain validation service
        var validationResult = workoutValidationService.validateForCreation(workout);
        if (validationResult.isInvalid()) {
            return Mono.error(new InvalidWorkoutStateException(validationResult.getErrorMessage()));
        }

        // Simple state management
        workout.startWorkout();

        return workoutRepository.save(workout)
                .doOnSuccess(saved -> log.info("Started workout session: {}", saved.getId()));
    }


    /**
     * Get user workouts with date filtering
     */
    public Flux<Workout> getUserWorkouts(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null) {
            return workoutRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, startDate, endDate);
        } else if (startDate != null) {
            return workoutRepository.findByUserIdAndWorkoutDateGreaterThanEqualOrderByWorkoutDateDesc(userId, startDate);
        } else if (endDate != null) {
            return workoutRepository.findByUserIdAndWorkoutDateLessThanEqualOrderByWorkoutDateDesc(userId, endDate);
        } else {
            return workoutRepository.findByUserIdOrderByWorkoutDateDesc(userId);
        }
    }

    /**
     * Get user workouts (overloaded method)
     */
    public Flux<Workout> getUserWorkouts(Long userId) {
        return workoutRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    /**
     * IMMEDIATE PR PROCESSING: Log set with real-time PersonalRecord detection
     * ENHANCED: Now validates workout status before allowing set operations
     */
    public Mono<Workout> logSet(String workoutId, Long userId, int exerciseIndex, LogSetRequest setRequest) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    var validationResult = workoutValidationService.validateForSetOperations(workout);
                    if (validationResult.isInvalid()) {
                        log.warn("Set logging denied for workout {} with status {}", workoutId, workout.getStatus());
                        return Mono.error(new InvalidWorkoutStateException(
                                validationResult.getErrorMessage(),
                                workout.getStatus().toString(),
                                WorkoutStatus.IN_PROGRESS.toString()
                        ));
                    }

                    if (exerciseIndex >= workout.getExercises().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid exercise index"));
                    }

                    WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                    WorkoutSet newSet = createWorkoutSetFromRequest(setRequest, exercise.getSets().size() + 1);
                    exercise.addSet(newSet);

                    return workoutRepository.save(workout)
                            .flatMap(savedWorkout ->
                                    processSetAndUpdatePRs(savedWorkout, newSet, exercise, workoutId, userId));
                });
    }

    /**
     * CLEAN DELEGATION: PersonalRecord processing
     *
     * Single Responsibility: Validate and delegate
     * Dependency Inversion: Depends on PersonalRecordService abstraction
     *
     * SAME PATTERN AS WORKOUT ORCHESTRATOR:
     * - Validate input
     * - Delegate to domain service
     * - Domain service handles event creation and publishing
     */
    private Mono<Void> processSetForImmediatePRs(WorkoutSet set, WorkoutExercise exercise,
                                                 String workoutId, Long userId) {
        // Input validation (Single Responsibility)
        if (!isValidForPRProcessing(set)) {
            log.debug("Set not valid for PR processing, skipping");
            return Mono.empty();
        }

        log.info("Delegating immediate PR processing: exercise={}, weight={}kg, reps={}",
                exercise.getExerciseName(), set.getWeightKg(), set.getReps());

        // CLEAN DELEGATION: PersonalRecordService handles everything
        return personalRecordService.processSetForImmediatePRs(
                        userId,
                        exercise.getExerciseId(),
                        exercise.getExerciseName(),
                        set,
                        workoutId
                )
                .doOnSuccess(events -> {
                    if (!events.isEmpty()) {
                        log.info("IMMEDIATE PR PIPELINE COMPLETED: {} events processed for user {}",
                                events.size(), userId);
                    } else {
                        log.debug("No immediate PRs detected for set");
                    }
                })
                .then()
                .doOnSuccess(v -> log.debug("Immediate PR processing delegation completed"))
                .onErrorResume(error -> {
                    // Fault Tolerance: PR processing failure doesn't break set logging
                    log.warn("PR processing failed (non-critical): {}", error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * DECOUPLED COMPLETION: Uses orchestrator which publishes events
     * Personal records processing happens asynchronously via event handler
     */
    public Mono<Workout> completeWorkout(String workoutId, Long userId, Map<String, Object> completionData) {
        log.info("Completing workout session: {} (using decoupled architecture)", workoutId);

        if (useOrchestrator) {
            // Use decoupled orchestrator - personal records handled via events
            return workoutOrchestrator.completeWorkout(workoutId, userId, completionData)
                    .doOnSuccess(workout -> log.info("✅ Workout completed using decoupled orchestrator: {}", workoutId));
        } else {
            // Legacy path for backward compatibility
            return completeLegacyWorkout(workoutId, userId, completionData);
        }
    }

    /**
     * Legacy completion method (for backward compatibility during migration)
     */
    //@Deprecated
    private Mono<Workout> completeLegacyWorkout(String workoutId, Long userId, Map<String, Object> completionData) {
        log.warn("Using legacy workout completion for: {} (consider migrating to orchestrator)", workoutId);

        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    if (!WorkoutStatus.IN_PROGRESS.equals(workout.getStatus())) {
                        return Mono.error(new InvalidWorkoutStateException("Workout is not in progress"));
                    }

                    // Basic completion logic
                    workout.setStatus(WorkoutStatus.COMPLETED);
                    workout.setCompletedAt(LocalDateTime.now());

                    if (completionData != null) {
                        applyCompletionData(workout, completionData);
                    }

                    return workoutRepository.save(workout);
                });
    }

    // ==================== CORE WORKOUT OPERATIONS ====================

    /**
     * Find workout by ID and user ID (security check)
     */
    public Mono<Workout> findByIdAndUserId(String workoutId, Long userId) {
        return workoutRepository.findByIdAndUserId(workoutId, userId)
                .switchIfEmpty(Mono.error(new WorkoutNotFoundException(
                        "Workout not found with ID: " + workoutId)));
    }

    /**
     * Start workout from saved plan
     */
    public Mono<Workout> startWorkoutFromPlan(Long userId, StartWorkoutRequest request) {
        log.info("Starting workout from plan for user: {}, planId: {}", userId, request.getWorkoutPlanId());

        return workoutPlanService.findById(request.getWorkoutPlanId())
                .doOnNext(plan -> log.debug("Found workout plan: '{}' with {} exercises",
                        plan.getTitle(), plan.getExercises() != null ? plan.getExercises().size() : 0))
                .flatMap(plan -> validateUserPlanAccess(plan, userId)
                        .then(Mono.defer(() -> createWorkoutFromPlan(userId, request, plan))))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Workout plan not found or not accessible: " + request.getWorkoutPlanId())))
                .doOnSuccess(workout -> log.info("Successfully created workout from plan with {} exercises",
                        workout.getExercises().size()))
                .doOnError(error -> log.error("Failed to create workout from plan: {}", error.getMessage()));
    }

    /**
     * Validate that the user has access to the workout plan
     */
    private Mono<Void> validateUserPlanAccess(WorkoutPlan plan, Long userId) {
        log.debug("Validating user {} access to plan: '{}' (public: {}, createdBy: {})",
                userId, plan.getTitle(), plan.getIsPublic(), plan.getCreatedBy());

        boolean hasAccess = Boolean.TRUE.equals(plan.getIsPublic()) ||
                userId.equals(plan.getCreatedBy());

        if (hasAccess) {
            log.debug("User {} has access to plan '{}'", userId, plan.getTitle());
            return Mono.empty();
        }

        return workoutPlanService.hasUserSavedPlan(userId, plan.getId())
                .flatMap(hasSaved -> {
                    if (hasSaved) {
                        log.debug("User {} has saved plan '{}' to personal collection", userId, plan.getTitle());
                        return Mono.empty();
                    } else {
                        log.warn("User {} does not have access to plan '{}'", userId, plan.getTitle());
                        return Mono.error(new SecurityException("Access denied to workout plan"));
                    }
                });
    }

    /**
     * Create workout from plan with customizations
     */
    private Mono<Workout> createWorkoutFromPlan(Long userId, StartWorkoutRequest request, WorkoutPlan plan) {
        Workout workout = new Workout();

        // Set basic workout properties
        workout.setUserId(userId);
        workout.setWorkoutPlanId(plan.getId());
        workout.setWorkoutName(request.getWorkoutName() != null ? request.getWorkoutName() : plan.getTitle());

        // Handle missing getType() method - use request type or default to STRENGTH
        String workoutType = request.getWorkoutType();
        if (workoutType == null) {
            // If your WorkoutPlan has a different method name, replace this
            // e.g., plan.getCategory(), plan.getWorkoutCategory(), etc.
            workoutType = "STRENGTH"; // Default fallback
        }
        workout.setWorkoutType(workoutType);

        workout.setLocation(request.getLocation());
        workout.setNotes(buildNotesFromPlan(plan, request.getNotes()));
        workout.setTags(buildTagsFromPlan(plan, request.getTags()));
        workout.setStatus(WorkoutStatus.IN_PROGRESS);
        workout.setWorkoutDate(LocalDateTime.now());
        workout.setStartedAt(LocalDateTime.now());

        // Convert planned exercises to workout exercises
        List<WorkoutExercise> workoutExercises = convertPlannedExercisesToWorkout(
                plan.getExercises(), request.getExcludeExerciseIds(), request.getCustomizations());
        workout.setExercises(workoutExercises);

        return workoutRepository.save(workout)
                .doOnSuccess(saved -> log.info("Created workout from plan with {} exercises", saved.getExercises().size()));
    }

    // ==================== EXERCISE AND SET MANAGEMENT ====================

    /**
     * IMMEDIATE PR PROCESSING: Update set with real-time PersonalRecord detection
     *
     * SAME CLEAN PATTERN: Delegate to PersonalRecordService
     */
    public Mono<Workout> updateSet(String workoutId, Long userId, int exerciseIndex, int setIndex, LogSetRequest setRequest) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    var validationResult = workoutValidationService.validateForSetOperations(workout);
                    if (validationResult.isInvalid()) {
                        log.warn("Set update denied for workout {} with status {}", workoutId, workout.getStatus());
                        return Mono.error(new InvalidWorkoutStateException(
                                validationResult.getErrorMessage(),
                                workout.getStatus().toString(),
                                WorkoutStatus.IN_PROGRESS.toString()
                        ));
                    }

                    if (exerciseIndex >= workout.getExercises().size() ||
                            setIndex >= workout.getExercises().get(exerciseIndex).getSets().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid exercise or set index"));
                    }

                    WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                    WorkoutSet existingSet = exercise.getSets().get(setIndex);
                    boolean wasCompleted = Boolean.TRUE.equals(existingSet.getCompleted());

                    updateWorkoutSetFromRequest(existingSet, setRequest);

                    if (shouldProcessUpdatedSetForPRs(existingSet, wasCompleted)) {
                        return processSetAndUpdatePRs(workout, existingSet, exercise, workoutId, userId);
                    } else {
                        return workoutRepository.save(workout);
                    }
                });
    }

    /**
     * Add exercise to an active workout
     */
    public Mono<Workout> addExerciseToWorkout(String workoutId, Long userId, WorkoutExercise exercise) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    if (!workout.isActive()) {
                        return Mono.error(new InvalidWorkoutStateException("Cannot add exercises to inactive workout"));
                    }

                    if (exercise.getSets() == null) {
                        exercise.setSets(new ArrayList<>());
                    }

                    workout.addExercise(exercise);
                    return workoutRepository.save(workout);
                });
    }

    /**
     * Delete a set from a workout
     * ENHANCED: Now validates workout status before allowing set deletion
     */
    public Mono<Void> deleteSet(String workoutId, Long userId, int exerciseIndex, int setIndex) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {

                    // VALIDATE WORKOUT STATUS FIRST
                    var validationResult = workoutValidationService.validateForSetOperations(workout);
                    if (validationResult.isInvalid()) {
                        log.warn("Set deletion denied for workout {} with status {}",
                                workoutId, workout.getStatus());
                        return Mono.error(new InvalidWorkoutStateException(
                                validationResult.getErrorMessage(),
                                workout.getStatus().toString(),
                                WorkoutStatus.IN_PROGRESS.toString()
                        ));
                    }

                    if (exerciseIndex >= workout.getExercises().size()) {
                        return Mono.error(new ExerciseNotFoundException("Invalid exercise index"));
                    }

                    WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                    if (setIndex >= exercise.getSets().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid set index"));
                    }

                    exercise.getSets().remove(setIndex);
                    // Renumber remaining sets
                    for (int i = setIndex; i < exercise.getSets().size(); i++) {
                        exercise.getSets().get(i).setSetNumber(i + 1);
                    }

                    return workoutRepository.save(workout);
                })
                .then();
    }

    // ==================== BUSINESS RULES (SINGLE RESPONSIBILITY) ====================

    /**
     * Single Responsibility: Validation logic for PR processing
     * FIXED: Uses enum-based setType instead of deprecated getWarmUp()
     */
    private boolean isValidForPRProcessing(WorkoutSet set) {
        return Boolean.TRUE.equals(set.getCompleted()) && set.countsTowardPersonalRecords();
    }

    /**
     * Single Responsibility: Business rule for updated set PR processing
     */
    private boolean shouldProcessUpdatedSetForPRs(WorkoutSet set, boolean wasAlreadyCompleted) {
        // Always process newly completed sets
        if (!wasAlreadyCompleted && Boolean.TRUE.equals(set.getCompleted())) {
            return true;
        }

        // Process updated completed sets (allows for corrections that create new PRs)
        if (wasAlreadyCompleted && Boolean.TRUE.equals(set.getCompleted())) {
            return true;
        }

        return false;
    }

    // ==================== QUERY OPERATIONS ====================

    public Flux<Workout> findRecentWorkouts(Long userId) {
        return workoutRepository.findTop10ByUserIdOrderByWorkoutDateDesc(userId);
    }

    public Mono<Long> countWorkoutsInDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return workoutRepository.countByUserIdAndWorkoutDateBetween(userId, startDate, endDate);
    }

    public Flux<Workout> findByUserAndExercise(Long userId, String exerciseId) {
        return workoutRepository.findByUserIdAndExerciseId(userId, exerciseId);
    }

    public Flux<Workout> findByUserAndVolumeRange(Long userId, BigDecimal minVolume, BigDecimal maxVolume) {
        return workoutRepository.findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(userId, minVolume, maxVolume);
    }

    public Mono<Workout> cancelWorkout(String workoutId, Long userId) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    workout.cancelWorkout();
                    return save(workout);
                });
    }

    public Mono<Workout> save(Workout workout) {
        return workoutRepository.save(workout);
    }

    public Mono<Void> deleteById(String id) {
        return workoutRepository.deleteById(id);
    }

    public Flux<Workout> findAll() {
        return workoutRepository.findAll();
    }

    // ==================== HELPER METHODS (PURE FUNCTIONS) ====================


    /**
     * Process set for PRs and populate personalRecords field
     * Reusable method for both logSet and updateSet
     */
    private Mono<Workout> processSetAndUpdatePRs(
            Workout workout,
            WorkoutSet set,
            WorkoutExercise exercise,
            String workoutId,
            Long userId) {

        if (!isValidForPRProcessing(set)) {
            log.debug("❌ Set not valid for PR processing, skipping. Completed: {}, SetType: {}",
                    set.getCompleted(), set.getSetType());
            return workoutRepository.save(workout);
        }

        log.info("🔍 Processing set for immediate PRs: exercise={}, weight={}kg, reps={}",
                exercise.getExerciseName(), set.getWeightKg(), set.getReps());

        return personalRecordService.processSetForImmediatePRs(
                        userId,
                        exercise.getExerciseId(),
                        exercise.getExerciseName(),
                        set,
                        workoutId
                )
                .map(prEvents -> {
                    log.info("📊 Received {} PR events from PersonalRecordService", prEvents.size());

                    if (!prEvents.isEmpty()) {
                        List<String> prTypes = prEvents.stream()
                                .map(event -> {
                                    log.info("   🏆 PR Type: {} ({}kg -> {}kg)",
                                            event.getRecordType(),
                                            event.getPreviousValue(),
                                            event.getNewValue());
                                    return event.getRecordType();
                                })
                                .distinct()
                                .collect(java.util.stream.Collectors.toList());

                        log.info("✅ Setting personalRecords on set BEFORE save: {}", prTypes);
                        set.setPersonalRecords(prTypes);

                        // NEW: Clean up old PR badges from previous sets in this exercise
                        cleanupOldPersonalRecordBadges(exercise, set, prTypes);

                        log.info("✅ PR DETECTED: {} achieved {} PRs: {}",
                                exercise.getExerciseName(),
                                prTypes.size(),
                                String.join(", ", prTypes));
                    } else {
                        log.info("ℹ️ No PRs detected for this set");
                        set.setPersonalRecords(null);
                    }

                    return workout;
                })
                .flatMap(updatedWorkout -> {
                    log.info("💾 Saving workout with PR information...");
                    return workoutRepository.save(updatedWorkout);
                })
                .doOnSuccess(savedWorkout -> {
                    log.info("✅ Workout saved successfully with cleaned PR badges");
                })
                .onErrorResume(error -> {
                    log.error("❌ PR processing failed: {}", error.getMessage(), error);
                    return workoutRepository.save(workout);
                });
    }

    /**
     * Remove PR badges from previous sets when a new PR of the same type is achieved
     * Only affects sets in the SAME exercise during the SAME workout session
     */
    private void cleanupOldPersonalRecordBadges(
            WorkoutExercise exercise,
            WorkoutSet currentSet,
            List<String> newPRTypes) {

        if (exercise.getSets() == null || newPRTypes == null || newPRTypes.isEmpty()) {
            return;
        }

        log.info("🧹 Cleaning up old PR badges for exercise: {}", exercise.getExerciseName());

        for (WorkoutSet previousSet : exercise.getSets()) {
            // Skip the current set
            if (previousSet.equals(currentSet)) {
                continue;
            }

            // Skip sets without PR badges
            if (previousSet.getPersonalRecords() == null || previousSet.getPersonalRecords().isEmpty()) {
                continue;
            }

            // Remove PR types that are now achieved by the current set
            List<String> filteredPRs = previousSet.getPersonalRecords().stream()
                    .filter(prType -> !newPRTypes.contains(prType))
                    .collect(java.util.stream.Collectors.toList());

            if (filteredPRs.isEmpty()) {
                log.info("   🗑️ Removing all PR badges from set {}", previousSet.getSetNumber());
                previousSet.setPersonalRecords(null);
            } else if (filteredPRs.size() < previousSet.getPersonalRecords().size()) {
                log.info("   🔄 Updating PR badges for set {} from {} to {}",
                        previousSet.getSetNumber(),
                        previousSet.getPersonalRecords(),
                        filteredPRs);
                previousSet.setPersonalRecords(filteredPRs);
            }
        }
    }

    /**
     * Pure function: Create WorkoutSet from request with set type classification
     */
    private WorkoutSet createWorkoutSetFromRequest(LogSetRequest request, int setNumber) {
        WorkoutSet set = new WorkoutSet();
        set.setSetNumber(setNumber);
        set.setWeightKg(request.getWeightKg());
        set.setReps(request.getReps());
        set.setDurationSeconds(request.getDurationSeconds());
        set.setDistanceMeters(request.getDistanceMeters());
        set.setRestSeconds(request.getRestSeconds());
        set.setRpe(request.getRpe());
        set.setCompleted(request.getCompleted());
        set.setSetType(request.getSetType()); // Direct assignment, no sync needed
        set.setNotes(request.getNotes());

        if (Boolean.TRUE.equals(request.getCompleted())) {
            set.markCompleted();
        }

        return set;
    }

    /**
     * Pure function: Update existing WorkoutSet
     */
    private void updateWorkoutSetFromRequest(WorkoutSet existingSet, LogSetRequest request) {
        existingSet.setWeightKg(request.getWeightKg());
        existingSet.setReps(request.getReps());
        existingSet.setDurationSeconds(request.getDurationSeconds());
        existingSet.setDistanceMeters(request.getDistanceMeters());
        existingSet.setRestSeconds(request.getRestSeconds());
        existingSet.setRpe(request.getRpe());
        existingSet.setCompleted(request.getCompleted());
        existingSet.setSetType(request.getSetType()); // Direct assignment, no sync needed
        existingSet.setNotes(request.getNotes());

        if (Boolean.TRUE.equals(request.getCompleted()) && existingSet.getCompletedAt() == null) {
            existingSet.markCompleted();
        }
    }

    private void applyCompletionData(Workout workout, Map<String, Object> completionData) {
        if (completionData.containsKey("rating")) {
            workout.setRating((Integer) completionData.get("rating"));
        }
        if (completionData.containsKey("notes")) {
            workout.setNotes((String) completionData.get("notes"));
        }
        if (completionData.containsKey("caloriesBurned")) {
            workout.setCaloriesBurned((Integer) completionData.get("caloriesBurned"));
        }
    }

    private List<String> buildTagsFromPlan(WorkoutPlan plan, List<String> requestTags) {
        List<String> tags = new ArrayList<>();

        // Handle case where WorkoutPlan might not have getTags() method
        try {
            // Try to get tags from plan if the method exists
            if (plan != null) {
                // You might need to replace this with the correct method name
                // e.g., plan.getCategories(), plan.getLabels(), etc.
                // For now, we'll just use request tags
            }
        } catch (Exception e) {
            // Method doesn't exist, continue without plan tags
        }

        if (requestTags != null) {
            tags.addAll(requestTags);
        }
        tags.add("from-plan");
        return tags;
    }

    private String buildNotesFromPlan(WorkoutPlan plan, String requestNotes) {
        StringBuilder notes = new StringBuilder();
        if (plan.getDescription() != null && !plan.getDescription().isEmpty()) {
            notes.append("Plan: ").append(plan.getDescription());
        }
        if (requestNotes != null && !requestNotes.isEmpty()) {
            if (notes.length() > 0) notes.append("\n\n");
            notes.append("Notes: ").append(requestNotes);
        }
        return notes.toString();
    }

    private List<WorkoutExercise> convertPlannedExercisesToWorkout(List<PlannedExercise> plannedExercises,
                                                                   List<String> excludeIds,
                                                                   Map<String, Object> customizations) {
        if (plannedExercises == null) {
            return new ArrayList<>();
        }

        return plannedExercises.stream()
                .filter(exercise -> excludeIds == null || !excludeIds.contains(exercise.getExerciseTemplateId()))
                .map(planned -> convertToWorkoutExercise(planned, customizations))
                .toList();
    }

    private WorkoutExercise convertToWorkoutExercise(PlannedExercise planned, Map<String, Object> customizations) {
        WorkoutExercise exercise = new WorkoutExercise();
        exercise.setExerciseId(planned.getExerciseTemplateId());
        exercise.setExerciseName(planned.getTitle());
        exercise.setNotes(planned.getNotes());

        // Convert planned sets to workout sets with customizations
        List<WorkoutSet> sets = new ArrayList<>();
        if (planned.getSets() != null && !planned.getSets().isEmpty()) {
            for (int i = 0; i < planned.getSets().size(); i++) {
                PlannedExercise.PlannedSet plannedSet = planned.getSets().get(i);
                WorkoutSet set = createSetFromPlannedSet(plannedSet, i + 1, customizations, planned.getExerciseTemplateId());
                sets.add(set);
            }
        }
        exercise.setSets(sets);

        return exercise;
    }

    private WorkoutSet createSetFromPlannedSet(PlannedExercise.PlannedSet plannedSet, int setNumber,
                                               Map<String, Object> customizations, String exerciseId) {
        WorkoutSet set = new WorkoutSet();
        set.setSetNumber(setNumber);

        // Apply base values with customizations
        if (plannedSet.getWeightKg() != null) {
            BigDecimal weight = BigDecimal.valueOf(plannedSet.getWeightKg());
            set.setWeightKg(applyWeightCustomization(weight, customizations, exerciseId));
        }

        if (plannedSet.getReps() != null) {
            set.setReps(applyRepsCustomization(plannedSet.getReps(), customizations, exerciseId));
        } else if (plannedSet.hasRepRange()) {
            // Use middle of rep range as default
            int middleReps = (plannedSet.getRepRangeStart() + plannedSet.getRepRangeEnd()) / 2;
            set.setReps(applyRepsCustomization(middleReps, customizations, exerciseId));
        }

        if (plannedSet.getDurationSeconds() != null) {
            set.setDurationSeconds(plannedSet.getDurationSeconds());
        }

        if (plannedSet.getDistanceMeters() != null) {
            set.setDistanceMeters(BigDecimal.valueOf(plannedSet.getDistanceMeters()));
        }

        set.setCompleted(false);

        return set;
    }

    private BigDecimal applyWeightCustomization(BigDecimal baseWeight, Map<String, Object> customizations, String exerciseId) {
        if (customizations == null) return baseWeight;

        Object globalAdjustment = customizations.get("weightAdjustmentPercent");
        if (globalAdjustment instanceof Number) {
            double adjustmentPercent = ((Number) globalAdjustment).doubleValue();
            baseWeight = baseWeight.multiply(BigDecimal.valueOf(1 + adjustmentPercent / 100));
        }

        Object exerciseOverride = customizations.get("exerciseWeights");
        if (exerciseOverride instanceof Map) {
            Map<String, Object> exerciseWeights = (Map<String, Object>) exerciseOverride;
            Object specificWeight = exerciseWeights.get(exerciseId);
            if (specificWeight instanceof Number) {
                baseWeight = BigDecimal.valueOf(((Number) specificWeight).doubleValue());
            }
        }

        return baseWeight;
    }

    private Integer applyRepsCustomization(Integer baseReps, Map<String, Object> customizations, String exerciseId) {
        if (customizations == null) return baseReps;

        Object globalAdjustment = customizations.get("repsAdjustment");
        if (globalAdjustment instanceof Number) {
            baseReps += ((Number) globalAdjustment).intValue();
        }

        Object exerciseOverride = customizations.get("exerciseReps");
        if (exerciseOverride instanceof Map) {
            Map<String, Object> exerciseReps = (Map<String, Object>) exerciseOverride;
            Object specificReps = exerciseReps.get(exerciseId);
            if (specificReps instanceof Number) {
                baseReps = ((Number) specificReps).intValue();
            }
        }

        return Math.max(1, baseReps);
    }
}