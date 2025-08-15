package com.muscledia.workout_service.service;

import com.muscledia.workout_service.dto.request.StartWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.LogSetRequest;
import com.muscledia.workout_service.event.ExerciseCompletedEvent;
import com.muscledia.workout_service.event.WorkoutCompletedEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutService {
    private final WorkoutRepository workoutRepository;
    private final TransactionalEventPublisher transactionalEventPublisher;
    private final TransactionalOperator transactionalOperator; // Inject for reactive transactions
    private final PersonalRecordService personalRecordService;
    private final WorkoutPlanService workoutPlanService;

    // WORKOUT SESSION MANAGEMENT

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
                .exercises(new ArrayList<>()) // Initialize empty exercises list
                .status(WorkoutStatus.IN_PROGRESS)
                .workoutDate(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .totalVolume(BigDecimal.ZERO)
                .totalSets(0)
                .totalReps(0)
                .build();

        return workoutRepository.save(workout)
                .doOnSuccess(saved -> log.info("Started workout session: {}", saved.getId()));
    }

    /**
     * Find workout by ID and user ID (security check)
     */
    public Mono<Workout> findByIdAndUserId(String workoutId, Long userId) {
        return workoutRepository.findByIdAndUserId(workoutId, userId)
                .switchIfEmpty(Mono.error(new WorkoutNotFoundException(
                        "Workout not found with ID: " + workoutId)));
    }

    /**
     * CORE FEATURE: Start workout from saved plan
     * This is the key integration that connects workout planning with execution
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

        // Allow access if:
        // 1. Plan is public, OR
        // 2. User created the plan, OR
        // 3. User has saved it to personal collection
        boolean hasAccess = Boolean.TRUE.equals(plan.getIsPublic()) ||
                userId.equals(plan.getCreatedBy());

        if (hasAccess) {
            log.debug("User {} has access to plan '{}'", userId, plan.getTitle());
            return Mono.empty();
        }

        // Check if user has saved this plan to personal collection
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

    private Mono<Workout> createWorkoutFromPlan(Long userId, StartWorkoutRequest request, WorkoutPlan plan) {
        log.debug("Converting plan '{}' to active workout for user {}", plan.getTitle(), userId);

        Workout workout = new Workout();

        // Set basic workout properties
        workout.setUserId(userId);
        workout.setWorkoutPlanId(plan.getId());
        workout.setWorkoutName(determineWorkoutName(request.getWorkoutName(), plan.getTitle()));
        workout.setWorkoutType(request.getWorkoutType() != null ? request.getWorkoutType() : "STRENGTH");
        workout.setStatus(WorkoutStatus.IN_PROGRESS);
        workout.setWorkoutDate(LocalDateTime.now());
        workout.setStartedAt(LocalDateTime.now());

        // Set context from request
        workout.setLocation(request.getLocation());
        workout.setNotes(combineNotes(plan.getDescription(), request.getNotes()));
        workout.setTags(combineTags(null, request.getTags())); // Plans don't have tags in your model

        // CRITICAL: Convert plan exercises to workout exercises
        List<WorkoutExercise> workoutExercises = convertPlannedExercisesToWorkoutExercises(
                plan.getExercises(),
                request.getExcludeExerciseIds(),
                request.getCustomizations()
        );
        workout.setExercises(workoutExercises);

        // Initialize metrics
        workout.setTotalSets(calculateTotalSets(workoutExercises));
        workout.setTotalReps(0); // Will be updated as user completes sets
        workout.setTotalVolume(BigDecimal.ZERO); // Will be calculated during workout

        log.debug("Created workout with {} exercises, {} total planned sets",
                workoutExercises.size(), workout.getTotalSets());

        return workoutRepository.save(workout)
                .doOnSuccess(saved -> {
                    log.info("Saved workout from plan: {}", saved.getId());
                    // Update plan usage stats asynchronously
                    updatePlanUsageStats(plan.getId(), userId);
                });
    }

    /**
     * EXERCISE CONVERSION: Transform PlannedExercise to WorkoutExercise
     * This converts plan templates into executable workout exercises with template sets
     */
    private List<WorkoutExercise> convertPlannedExercisesToWorkoutExercises(
            List<PlannedExercise> plannedExercises,
            List<String> excludeExerciseIds,
            Map<String, Object> customizations) {

        if (plannedExercises == null || plannedExercises.isEmpty()) {
            log.warn("No exercises found in workout plan");
            return new ArrayList<>();
        }

        log.debug("Converting {} planned exercises to workout exercises", plannedExercises.size());

        return plannedExercises.stream()
                .filter(plannedEx -> !isExerciseExcluded(plannedEx.getExerciseTemplateId(), excludeExerciseIds))
                .map(plannedEx -> {
                    log.debug("Converting exercise: {} ({})", plannedEx.getTitle(), plannedEx.getExerciseTemplateId());

                    WorkoutExercise workoutEx = new WorkoutExercise();

                    // Map planned exercise to workout exercise
                    workoutEx.setExerciseId(plannedEx.getExerciseTemplateId());
                    workoutEx.setExerciseName(plannedEx.getTitle());
                    workoutEx.setExerciseCategory("STRENGTH"); // Default, could be enhanced
                    workoutEx.setExerciseOrder(plannedEx.getIndex() != null ? plannedEx.getIndex() : 1);
                    workoutEx.setNotes(plannedEx.getNotes());
                    workoutEx.setStartedAt(LocalDateTime.now());

                    // CRITICAL: Create template sets from planned sets
                    List<WorkoutSet> templateSets = createTemplateSetsFromPlannedSets(plannedEx, customizations);
                    workoutEx.setSets(templateSets);

                    log.debug("Converted exercise '{}' with {} template sets",
                            workoutEx.getExerciseName(), templateSets.size());

                    return workoutEx;
                })
                .collect(Collectors.toList());
    }


    /**
     * SET TEMPLATE CREATION: Convert PlannedExercise.PlannedSet to WorkoutSet templates
     */
    private List<WorkoutSet> createTemplateSetsFromPlannedSets(PlannedExercise plannedEx, Map<String, Object> customizations) {
        List<WorkoutSet> templateSets = new ArrayList<>();

        if (plannedEx.getSets() == null || plannedEx.getSets().isEmpty()) {
            log.debug("No planned sets found for exercise '{}', creating default template", plannedEx.getTitle());
            // Create a default set if none planned
            templateSets.add(createDefaultTemplateSet(1, customizations, plannedEx.getExerciseTemplateId()));
            return templateSets;
        }

        log.debug("Creating {} template sets for exercise '{}'",
                plannedEx.getSets().size(), plannedEx.getTitle());

        for (int i = 0; i < plannedEx.getSets().size(); i++) {
            PlannedExercise.PlannedSet plannedSet = plannedEx.getSets().get(i);
            WorkoutSet templateSet = new WorkoutSet();

            templateSet.setSetNumber(i + 1);

            // Map planned set data to workout set template
            if (plannedSet.getWeightKg() != null) {
                BigDecimal weight = BigDecimal.valueOf(plannedSet.getWeightKg());
                templateSet.setWeightKg(applyWeightCustomization(weight, customizations, plannedEx.getExerciseTemplateId()));
            }

            if (plannedSet.getReps() != null) {
                templateSet.setReps(applyRepsCustomization(plannedSet.getReps(), customizations, plannedEx.getExerciseTemplateId()));
            } else if (plannedSet.hasRepRange()) {
                // Use the middle of the rep range as the target
                int targetReps = (plannedSet.getRepRangeStart() + plannedSet.getRepRangeEnd()) / 2;
                templateSet.setReps(applyRepsCustomization(targetReps, customizations, plannedEx.getExerciseTemplateId()));
                templateSet.setNotes("Target: " + plannedSet.getRepRangeString() + " reps");
            }

            if (plannedSet.getDurationSeconds() != null) {
                templateSet.setDurationSeconds(plannedSet.getDurationSeconds());
            }

            if (plannedSet.getDistanceMeters() != null) {
                templateSet.setDistanceMeters(BigDecimal.valueOf(plannedSet.getDistanceMeters()));
            }

            // Set rest time from exercise level or default
            templateSet.setRestSeconds(plannedEx.getRestSeconds() != null ? plannedEx.getRestSeconds() : 90);

            // Template set defaults
            templateSet.setCompleted(false);
            templateSet.setFailure(false);
            templateSet.setDropSet(false);
            templateSet.setWarmUp("warmup".equalsIgnoreCase(plannedSet.getType()));
            templateSet.setSetType(plannedSet.getType() != null ? plannedSet.getType().toUpperCase() : "PLANNED");


            // Apply any exercise-specific customizations
            applySetCustomizations(templateSet, plannedEx.getExerciseTemplateId(), customizations);

            templateSets.add(templateSet);
        }

        log.debug("Created {} template sets for '{}'", templateSets.size(), plannedEx.getTitle());
        return templateSets;
    }

    /**
     * Create a default template set when no planned sets exist
     */
    private WorkoutSet createDefaultTemplateSet(int setNumber, Map<String, Object> customizations, String exerciseId) {
        WorkoutSet templateSet = new WorkoutSet();
        templateSet.setSetNumber(setNumber);
        templateSet.setWeightKg(BigDecimal.ZERO); // User will set during workout
        templateSet.setReps(10); // Default target
        templateSet.setRestSeconds(90); // Default rest
        templateSet.setCompleted(false);
        templateSet.setSetType("PLANNED");
        templateSet.setNotes("Template set - adjust as needed");

        applySetCustomizations(templateSet, exerciseId, customizations);
        return templateSet;
    }

    /**
     * CUSTOMIZATION HELPERS: Apply user customizations to the plan templates
     */
    private BigDecimal applyWeightCustomization(BigDecimal baseWeight, Map<String, Object> customizations, String exerciseId) {
        if (customizations == null) return baseWeight;

        // Global weight adjustment (e.g., -10% for deload week)
        Object globalAdjustment = customizations.get("weightAdjustmentPercent");
        if (globalAdjustment instanceof Number) {
            double adjustmentPercent = ((Number) globalAdjustment).doubleValue();
            baseWeight = baseWeight.multiply(BigDecimal.valueOf(1 + adjustmentPercent / 100));
        }

        // Exercise-specific weight override
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

        // Global reps adjustment
        Object globalAdjustment = customizations.get("repsAdjustment");
        if (globalAdjustment instanceof Number) {
            baseReps += ((Number) globalAdjustment).intValue();
        }

        // Exercise-specific reps override
        Object exerciseOverride = customizations.get("exerciseReps");
        if (exerciseOverride instanceof Map) {
            Map<String, Object> exerciseReps = (Map<String, Object>) exerciseOverride;
            Object specificReps = exerciseReps.get(exerciseId);
            if (specificReps instanceof Number) {
                baseReps = ((Number) specificReps).intValue();
            }
        }

        return Math.max(1, baseReps); // Ensure at least 1 rep
    }

    private void applySetCustomizations(WorkoutSet set, String exerciseId, Map<String, Object> customizations) {
        if (customizations == null) return;

        // Apply any additional customizations like set type overrides, etc.
        Object setTypeOverrides = customizations.get("setTypes");
        if (setTypeOverrides instanceof Map) {
            Map<String, String> typeOverrides = (Map<String, String>) setTypeOverrides;
            String overrideType = typeOverrides.get(exerciseId);
            if (overrideType != null) {
                set.setSetType(overrideType);
            }
        }
    }

    /**
     * HELPER METHODS
     */
    private boolean isExerciseExcluded(String exerciseId, List<String> excludeExerciseIds) {
        return excludeExerciseIds != null && excludeExerciseIds.contains(exerciseId);
    }

    private String determineWorkoutName(String requestedName, String planTitle) {
        if (requestedName != null && !requestedName.trim().isEmpty()) {
            return requestedName;
        }
        return planTitle + " - " + LocalDateTime.now().toLocalDate();
    }

    private String combineNotes(String planDescription, String requestNotes) {
        StringBuilder combinedNotes = new StringBuilder();

        if (planDescription != null && !planDescription.trim().isEmpty()) {
            combinedNotes.append("Plan: ").append(planDescription);
        }

        if (requestNotes != null && !requestNotes.trim().isEmpty()) {
            if (!combinedNotes.isEmpty()) {
                combinedNotes.append("\n\n");
            }
            combinedNotes.append("Notes: ").append(requestNotes);
        }

        return !combinedNotes.isEmpty() ? combinedNotes.toString() : null;
    }

    private List<String> combineTags(List<String> planTags, List<String> requestTags) {
        List<String> combinedTags = new ArrayList<>();

        if (planTags != null) {
            combinedTags.addAll(planTags);
        }

        if (requestTags != null) {
            for (String tag : requestTags) {
                if (!combinedTags.contains(tag)) {
                    combinedTags.add(tag);
                }
            }
        }

        // Add a tag indicating this workout came from a plan
        if (!combinedTags.contains("from-plan")) {
            combinedTags.add("from-plan");
        }

        return combinedTags;
    }

    private Integer calculateTotalSets(List<WorkoutExercise> exercises) {
        return exercises.stream()
                .mapToInt(ex -> ex.getSets() != null ? ex.getSets().size() : 0)
                .sum();
    }

    private void updatePlanUsageStats(String planId, Long userId) {
        // Async update of plan usage statistics
        workoutPlanService.incrementPlanUsage(planId, userId)
                .subscribe(
                        success -> log.debug("Updated usage stats for plan: {}", planId),
                        error -> log.warn("Failed to update usage stats for plan {}: {}", planId, error.getMessage())
                );
    }



    /**
     * Get user's workouts with optional filtering
     */
    public Flux<Workout> getUserWorkouts(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate != null || endDate != null) {
            LocalDateTime start = startDate != null ? startDate.atStartOfDay() : LocalDateTime.now().minusYears(1);
            LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : LocalDateTime.now();
            return workoutRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(userId, start, end);
        }
        return workoutRepository.findByUserIdOrderByWorkoutDateDesc(userId);
    }

    // SET LOGGING - Core functionality for the user story

    /**
     * Log a new set for a specific exercise - This implements the user story!
     */
    public Mono<Workout> logSet(String workoutId, Long userId, int exerciseIndex, LogSetRequest setRequest) {
        log.info("Logging set for workout {} exercise {}: {}kg x {} reps",
                workoutId, exerciseIndex, setRequest.getWeightKg(), setRequest.getReps());

        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate workout state
                            if (!workout.isActive()) {
                                return Mono.error(new InvalidWorkoutStateException(
                                        "Cannot log sets for a non-active workout. Current status: " + workout.getStatus()));
                            }

                            // Validate exercise index
                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException(
                                        String.format("Exercise index %d not found. Workout has %d exercises.",
                                                exerciseIndex, workout.getExercises().size())));
                            }

                            // Create the new set with precise performance data
                            WorkoutSet newSet = createWorkoutSetFromRequest(setRequest);

                            // Add set to the exercise
                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            exercise.addSet(newSet);

                            // Recalculate workout metrics
                            workout.recalculateMetrics();

                            return workoutRepository.save(workout);
                        })
                        .publishOn(Schedulers.boundedElastic())
                        .doOnSuccess(savedWorkout -> {
                            log.info("Successfully logged set for workout {}: {}kg x {} reps",
                                    workoutId, setRequest.getWeightKg(), setRequest.getReps());

                            // Check for personal records asynchronously
                            checkForPersonalRecords(savedWorkout, exerciseIndex)
                                    .subscribe(
                                            result -> log.info("PR check completed for workout {}", workoutId),
                                            error -> log.warn("PR check failed for workout {}: {}", workoutId, error.getMessage())
                                    );
                        })
        ).single();
    }

    /**
     * Update an existing set
     */
    public Mono<Workout> updateSet(String workoutId, Long userId, int exerciseIndex, int setIndex, LogSetRequest setRequest) {
        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate indices
                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException("Invalid exercise index: " + exerciseIndex));
                            }

                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            if (setIndex < 0 || setIndex >= exercise.getSets().size()) {
                                return Mono.error(new IllegalArgumentException("Invalid set index: " + setIndex));
                            }

                            // Update the set
                            WorkoutSet existingSet = exercise.getSets().get(setIndex);
                            updateWorkoutSetFromRequest(existingSet, setRequest);

                            // Recalculate metrics
                            workout.recalculateMetrics();

                            return workoutRepository.save(workout);
                        })
                        .doOnSuccess(saved -> log.info("Successfully updated set for workout {}", workoutId))
        ).single();
    }

    /**
     * Delete a set from an exercise
     */
    public Mono<Void> deleteSet(String workoutId, Long userId, int exerciseIndex, int setIndex) {
        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate indices
                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException("Invalid exercise index: " + exerciseIndex));
                            }

                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            if (setIndex < 0 || setIndex >= exercise.getSets().size()) {
                                return Mono.error(new IllegalArgumentException("Invalid set index: " + setIndex));
                            }

                            // Remove set and renumber
                            exercise.getSets().remove(setIndex);
                            renumberSets(exercise.getSets());

                            // Recalculate metrics
                            workout.recalculateMetrics();

                            return workoutRepository.save(workout);
                        })
                        .then()
        ).then();
    }

    /**
     * Add a new exercise to an active workout
     */
    public Mono<Workout> addExerciseToWorkout(String workoutId, Long userId, WorkoutExercise exercise) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    if (!workout.isActive()) {
                        return Mono.error(new InvalidWorkoutStateException("Cannot add exercises to inactive workout"));
                    }

                    // Initialize sets list if null
                    if (exercise.getSets() == null) {
                        exercise.setSets(new ArrayList<>());
                    }

                    workout.addExercise(exercise);
                    return workoutRepository.save(workout);
                })
                .doOnSuccess(saved -> log.info("Added exercise {} to workout {}",
                        exercise.getExerciseName(), workoutId));
    }

    // WORKOUT COMPLETION

    /**
     * Complete a workout session with comprehensive event publishing
     */
    public Mono<Workout> completeWorkout(String workoutId, Long userId, Map<String, Object> completionData) {
        log.info("Completing workout session: {} for user: {}", workoutId, userId);

        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // Validate workout can be completed
                            if (workout.isCompleted()) {
                                return Mono.error(new InvalidWorkoutStateException("Workout is already completed"));
                            }

                            if (workout.getExercises() == null || workout.getExercises().isEmpty()) {
                                return Mono.error(new InvalidWorkoutStateException(
                                        "Cannot complete workout without any exercises"));
                            }

                            // Apply completion data
                            if (completionData != null) {
                                applyCompletionData(workout, completionData);
                            }

                            // Complete the workout (calculates metrics)
                            workout.completeWorkout();

                            return workoutRepository.save(workout);
                        })
                        .flatMap(completedWorkout -> {
                            log.info("Workout saved to database with ID: {} for user: {}",
                                    completedWorkout.getId(), completedWorkout.getUserId());

                            // Create and publish WorkoutCompletedEvent as per acceptance criteria
                            return publishWorkoutCompletedEvent(completedWorkout)
                                    .then(publishExerciseCompletedEvents(completedWorkout))
                                    .then(checkForPersonalRecordsAllExercises(completedWorkout))
                                    .then(Mono.just(completedWorkout));
                        })
                        .doOnSuccess(workout ->
                                log.info("ACCEPTANCE CRITERIA MET: Workout document saved to database with correct userId: {} and WorkoutCompletedEvent published to workout-events topic",
                                        workout.getUserId()))
                        .doOnError(error ->
                                log.error("Failed to complete workout {}: {}", workoutId, error.getMessage()))
        ).single();
    }

    // ANALYTICS AND PERSONAL RECORDS

    /**
     * Check for personal records in a specific exercise
     */
    private Mono<Void> checkForPersonalRecords(Workout workout, int exerciseIndex) {
        if (exerciseIndex >= workout.getExercises().size()) {
            return Mono.empty();
        }

        WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
        return personalRecordService.checkAndUpdatePersonalRecords(
                workout.getUserId(),
                exercise.getExerciseId(),
                exercise.getSets()
        ).then();
    }

    /**
     * Check for personal records across all exercises in the workout
     */
    private Mono<Void> checkForPersonalRecordsAllExercises(Workout workout) {
        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> personalRecordService.checkAndUpdatePersonalRecords(
                        workout.getUserId(),
                        exercise.getExerciseId(),
                        exercise.getSets()
                ))
                .then();
    }

    // EVENT PUBLISHING

    /**
     * ENHANCED: Create WorkoutCompletedEvent with all required data
     * This ensures the event contains all the data needed for gamification
     */
    private Mono<Void> publishWorkoutCompletedEvent(Workout workout) {
        // Validate required data before creating event
        if (workout.getStartedAt() == null || workout.getCompletedAt() == null) {
            log.warn("Workout {} missing start/end times, using workout date as fallback", workout.getId());
        }

        WorkoutCompletedEvent event = WorkoutCompletedEvent.builder()
                .userId(workout.getUserId())
                .workoutId(workout.getId())
                .workoutType(workout.getWorkoutType())
                .durationMinutes(workout.getActualDurationMinutes())
                .totalVolume(workout.getTotalVolume())
                .totalSets(workout.getTotalSets())
                .totalReps(workout.getTotalReps())
                .exercisesCompleted(workout.getExercises().size())
                .caloriesBurned(workout.getCaloriesBurned()) // Add this if available
                // Convert LocalDateTime to Instant for the event
                .workoutStartTime(workout.getStartedAt() != null ?
                        workout.getStartedAt().toInstant(ZoneOffset.UTC) :
                        workout.getWorkoutDate().toInstant(ZoneOffset.UTC))
                .workoutEndTime(workout.getCompletedAt() != null ?
                        workout.getCompletedAt().toInstant(ZoneOffset.UTC) :
                        Instant.now())
                .workedMuscleGroups(workout.getWorkedMuscleGroups())
                .timestamp(Instant.now())
                .build();

        // Validate event before publishing
        if (!event.isValid()) {
            log.error("WorkoutCompletedEvent validation failed: {}", event);
            return Mono.error(new IllegalStateException("Invalid WorkoutCompletedEvent created"));
        }

        log.info("Publishing WorkoutCompletedEvent to workout-events topic: workoutId={}, userId={}",
                event.getWorkoutId(), event.getUserId());

        return transactionalEventPublisher.publishWorkoutCompleted(event)
                .doOnSuccess(v ->
                        log.info("WorkoutCompletedEvent successfully queued for publication to workout-events Kafka topic"))
                .doOnError(error ->
                        log.error("Failed to publish WorkoutCompletedEvent: {}", error.getMessage()));
    }

    /**
     * Publish individual exercise completed events
     */
    private Mono<Void> publishExerciseCompletedEvents(Workout workout) {
        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> {
                    // Calculate exercise metrics properly
                    int setsCompleted = exercise.getSets() != null ? exercise.getSets().size() : 0;
                    int totalReps = exercise.getTotalReps();
                    BigDecimal totalVolume = exercise.getTotalVolume();
                    BigDecimal maxWeight = exercise.getMaxWeight();

                    // Get duration for duration-based exercises (like warm-up)
                    Integer totalDuration = null;
                    if (exercise.getSets() != null) {
                        totalDuration = exercise.getSets().stream()
                                .filter(set -> set.getDurationSeconds() != null)
                                .mapToInt(WorkoutSet::getDurationSeconds)
                                .sum();
                    }

                    ExerciseCompletedEvent event = ExerciseCompletedEvent.builder()
                            .userId(workout.getUserId())
                            .workoutId(workout.getId())
                            .exerciseId(exercise.getExerciseId())
                            .exerciseName(exercise.getExerciseName())
                            .exerciseCategory(exercise.getExerciseCategory())
                            .setsCompleted(setsCompleted)
                            .totalReps(totalReps)
                            .totalVolume(totalVolume)
                            .maxWeight(maxWeight)
                            .primaryMuscleGroup(exercise.getPrimaryMuscleGroup())
                            // Add duration for warmup/cardio exercises
                            .durationSeconds(totalDuration != null && totalDuration > 0 ? totalDuration : null)
                            .equipment(exercise.getEquipment())
                            .build();

                    // Log validation details for debugging
                    log.debug("Publishing ExerciseCompletedEvent: exerciseId={}, category={}, sets={}, reps={}, duration={}, valid={}",
                            event.getExerciseId(), event.getExerciseCategory(), event.getSetsCompleted(),
                            event.getTotalReps(), event.getDurationSeconds(), event.isValid());

                    return transactionalEventPublisher.publishExerciseCompleted(event);
                })
                .then()
                .doOnSuccess(v -> log.info("Published ExerciseCompletedEvents for workout: {}", workout.getId()));
    }

    // HELPER METHODS

    /**
     * Create WorkoutSet from LogSetRequest
     */
    private WorkoutSet createWorkoutSetFromRequest(LogSetRequest request) {
        WorkoutSet set = WorkoutSet.builder()
                .weightKg(request.getWeightKg())
                .reps(request.getReps())
                .durationSeconds(request.getDurationSeconds())
                .distanceMeters(request.getDistanceMeters())
                .restSeconds(request.getRestSeconds())
                .rpe(request.getRpe())
                .completed(request.getCompleted())
                .failure(request.getFailure())
                .dropSet(request.getDropSet())
                .warmUp(request.getWarmUp())
                .setType(request.getSetType())
                .notes(request.getNotes())
                .startedAt(LocalDateTime.now())
                .build();

        if (Boolean.TRUE.equals(request.getCompleted())) {
            set.markCompleted();
        }

        return set;
    }

    /**
     * Update existing WorkoutSet from LogSetRequest
     */
    private void updateWorkoutSetFromRequest(WorkoutSet existingSet, LogSetRequest request) {
        existingSet.setWeightKg(request.getWeightKg());
        existingSet.setReps(request.getReps());
        existingSet.setDurationSeconds(request.getDurationSeconds());
        existingSet.setDistanceMeters(request.getDistanceMeters());
        existingSet.setRestSeconds(request.getRestSeconds());
        existingSet.setRpe(request.getRpe());
        existingSet.setCompleted(request.getCompleted());
        existingSet.setFailure(request.getFailure());
        existingSet.setDropSet(request.getDropSet());
        existingSet.setWarmUp(request.getWarmUp());
        existingSet.setSetType(request.getSetType());
        existingSet.setNotes(request.getNotes());

        if (Boolean.TRUE.equals(request.getCompleted()) && existingSet.getCompletedAt() == null) {
            existingSet.markCompleted();
        }
    }

    /**
     * Renumber sets after deletion
     */
    private void renumberSets(List<WorkoutSet> sets) {
        for (int i = 0; i < sets.size(); i++) {
            sets.get(i).setSetNumber(i + 1);
        }
    }

    /**
     * Apply completion data to workout
     */
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



    public Mono<Long> countWorkoutsInDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return workoutRepository.countByUserIdAndWorkoutDateBetween(userId, startDate, endDate);
    }

    public Flux<Workout> findByUserAndExercise(Long userId, String exerciseId) {
        return workoutRepository.findByUserIdAndExerciseId(userId, exerciseId);
    }


    /**
     * ADDED: Find all workouts (admin use)
     */
    public Flux<Workout> findAll() {
        return workoutRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all workouts"));
    }


    // [Keep all your existing helper methods: updateSet, deleteSet, checkForPersonalRecords,
    //  publishWorkoutCompletedEvent, createWorkoutSetFromRequest, etc.]

    // ADDITIONAL QUERY METHODS (keeping your existing ones)
    public Flux<Workout> findByUserAndVolumeRange(Long userId, BigDecimal minVolume, BigDecimal maxVolume) {
        return workoutRepository.findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(userId, minVolume, maxVolume);
    }

    public Flux<Workout> findRecentWorkouts(Long userId) {
        return workoutRepository.findTop10ByUserIdOrderByWorkoutDateDesc(userId);
    }

    public Mono<Workout> save(Workout workout) {
        return workoutRepository.save(workout)
                .doOnSuccess(saved -> log.debug("Saved workout: {}", saved.getId()));
    }

    public Mono<Void> deleteById(String id) {
        return workoutRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted workout with id: {}", id));
    }

    public Mono<Workout> cancelWorkout(String workoutId, Long userId) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    workout.cancelWorkout();
                    return save(workout);
                })
                .doOnSuccess(workout -> log.info("Cancelled workout: {}", workoutId));
    }



}