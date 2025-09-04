package com.muscledia.workout_service.service;

import com.muscledia.workout_service.domain.service.WorkoutCalculationService;
import com.muscledia.workout_service.domain.service.WorkoutOrchestrator;
import com.muscledia.workout_service.domain.service.WorkoutValidationService;
import com.muscledia.workout_service.domain.vo.WorkoutMetrics;
import com.muscledia.workout_service.dto.request.StartWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.LogSetRequest;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    // NEW: Domain Services for business logic
    private final WorkoutOrchestrator workoutOrchestrator;
    private final WorkoutCalculationService workoutCalculationService;
    private final WorkoutValidationService workoutValidationService;
    private final FeatureToggleService featureToggleService;

    // Feature flags for migration
    @Value("${workout.use-new-calculation:true}")
    private boolean useNewCalculation;

    @Value("${workout.use-orchestrator:false}")
    private boolean useOrchestrator;

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
                .exercises(new ArrayList<>())
                .status(WorkoutStatus.IN_PROGRESS)
                .workoutDate(LocalDateTime.now())
                .build();

        // NEW: Use domain validation service
        var validationResult = workoutValidationService.validateForCreation(workout);
        if (validationResult.isInvalid()) {
            return Mono.error(new InvalidWorkoutStateException(validationResult.getErrorMessage()));
        }

        // NEW: Use simple state management (no more complex business logic in model)
        workout.startWorkout(); // This is now just a simple state change

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
        workout.setLocation(request.getLocation());
        workout.setNotes(combineNotes(plan.getDescription(), request.getNotes()));
        workout.setTags(combineTags(null, request.getTags()));

        // Convert plan exercises to workout exercises
        List<WorkoutExercise> workoutExercises = convertPlannedExercisesToWorkoutExercises(
                plan.getExercises(),
                request.getExcludeExerciseIds(),
                request.getCustomizations()
        );
        workout.setExercises(workoutExercises);

        // NEW: Use domain validation before saving
        var validationResult = workoutValidationService.validateForCreation(workout);
        if (validationResult.isInvalid()) {
            return Mono.error(new InvalidWorkoutStateException(validationResult.getErrorMessage()));
        }

        // NEW: Simple state management
        workout.startWorkout();

        return workoutRepository.save(workout)
                .doOnSuccess(saved -> {
                    log.info("Saved workout from plan: {}", saved.getId());
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
                    workoutEx.setExerciseName(plannedEx.getTitle()); // THIS IS THE IMPORTANT PART!
                    workoutEx.setExerciseCategory("STRENGTH");
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
            templateSets.add(createDefaultTemplateSet(1, customizations, plannedEx.getExerciseTemplateId()));
            return templateSets;
        }

        log.debug("Creating {} template sets for exercise '{}'",
                plannedEx.getSets().size(), plannedEx.getTitle());

        for (int i = 0; i < plannedEx.getSets().size(); i++) {
            PlannedExercise.PlannedSet plannedSet = plannedEx.getSets().get(i);
            WorkoutSet templateSet = new WorkoutSet();

            templateSet.setSetNumber(i + 1);

            if (plannedSet.getWeightKg() != null) {
                BigDecimal weight = BigDecimal.valueOf(plannedSet.getWeightKg());
                templateSet.setWeightKg(applyWeightCustomization(weight, customizations, plannedEx.getExerciseTemplateId()));
            }

            if (plannedSet.getReps() != null) {
                templateSet.setReps(applyRepsCustomization(plannedSet.getReps(), customizations, plannedEx.getExerciseTemplateId()));
            } else if (plannedSet.hasRepRange()) {
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

            templateSet.setRestSeconds(plannedEx.getRestSeconds() != null ? plannedEx.getRestSeconds() : 90);
            templateSet.setCompleted(false);
            templateSet.setFailure(false);
            templateSet.setDropSet(false);
            templateSet.setWarmUp("warmup".equalsIgnoreCase(plannedSet.getType()));
            templateSet.setSetType(plannedSet.getType() != null ? plannedSet.getType().toUpperCase() : "PLANNED");

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
        templateSet.setWeightKg(BigDecimal.ZERO);
        templateSet.setReps(10);
        templateSet.setRestSeconds(90);
        templateSet.setCompleted(false);
        templateSet.setSetType("PLANNED");
        templateSet.setNotes("Template set - adjust as needed");

        applySetCustomizations(templateSet, exerciseId, customizations);
        return templateSet;
    }



    private void applySetCustomizations(WorkoutSet set, String exerciseId, Map<String, Object> customizations) {
        if (customizations == null) return;

        Object setTypeOverrides = customizations.get("setTypes");
        if (setTypeOverrides instanceof Map) {
            Map<String, String> typeOverrides = (Map<String, String>) setTypeOverrides;
            String overrideType = typeOverrides.get(exerciseId);
            if (overrideType != null) {
                set.setSetType(overrideType);
            }
        }
    }

    // WORKOUT OPERATIONS

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

    /**
     * ENHANCED: Log set with proper exercise name passing for personal records
     */
    public Mono<Workout> logSet(String workoutId, Long userId, int exerciseIndex, LogSetRequest setRequest) {
        log.info("Logging SET: workoutId={}, userId={}, exerciseIndex={}, weight={}kg, reps={}",
                workoutId, userId, exerciseIndex, setRequest.getWeightKg(), setRequest.getReps());

        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // NEW: Use domain validation service
                            var validationResult = workoutValidationService.validateForStart(workout);
                            if (validationResult.isInvalid()) {
                                return Mono.error(new InvalidWorkoutStateException(
                                        "Cannot log sets for workout in current state: " + validationResult.getErrorMessage()));
                            }

                            if (exerciseIndex < 0 || exerciseIndex >= workout.getExercises().size()) {
                                return Mono.error(new ExerciseNotFoundException(
                                        String.format("Exercise index %d not found. Workout has %d exercises.",
                                                exerciseIndex, workout.getExercises().size())));
                            }

                            WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);
                            WorkoutSet newSet = createWorkoutSetFromRequest(setRequest);

                            // NEW: Simple model operation (no business logic in model)
                            exercise.addSet(newSet);

                            return workoutRepository.save(workout);
                        })
                        .doOnSuccess(savedWorkout -> {
                            log.info("SET LOGGED SUCCESSFULLY: workoutId={}, exerciseIndex={}, setNumber={}",
                                    workoutId, exerciseIndex, savedWorkout.getExercises().get(exerciseIndex).getSets().size());

                            // Check for personal records asynchronously
                            checkForPersonalRecords(savedWorkout, exerciseIndex)
                                    .subscribe(
                                            result -> log.debug("PR check completed for workout {}", workoutId),
                                            error -> log.warn("PR check failed for workout {} but set was logged successfully: {}",
                                                    workoutId, error.getMessage())
                                    );
                        })
        ).single();
    }


    // WORKOUT COMPLETION
    /**
     * REFACTORED: Complete workout using domain services
     */

    public Mono<Workout> completeWorkout(String workoutId, Long userId, Map<String, Object> completionData) {
        if (useOrchestrator) {
            // NEW: Use DDD orchestrator
            return workoutOrchestrator.completeWorkout(workoutId, userId, completionData);
        } else {
            // OLD: Use existing implementation (keep as fallback)
            return completeWorkoutLegacy(workoutId, userId, completionData);
        }
    }

    public Mono<Workout> completeWorkoutLegacy(String workoutId, Long userId, Map<String, Object> completionData) {
        log.info("Completing workout session: {} for user: {}", workoutId, userId);

        return transactionalOperator.execute(status ->
                findByIdAndUserId(workoutId, userId)
                        .flatMap(workout -> {
                            // NEW: Use domain validation service
                            var validationResult = workoutValidationService.validateForCompletion(workout);
                            if (validationResult.isInvalid()) {
                                return Mono.error(new InvalidWorkoutStateException(validationResult.getErrorMessage()));
                            }

                            if (completionData != null) {
                                applyCompletionData(workout, completionData);
                            }

                            // NEW: Simple state change (no complex business logic)
                            workout.completeWorkout();

                            return workoutRepository.save(workout);
                        })
                        .flatMap(completedWorkout -> {
                            log.info("Workout saved to database with ID: {} for user: {}",
                                    completedWorkout.getId(), completedWorkout.getUserId());

                            return publishWorkoutCompletedEvent(completedWorkout)
                                    .then(checkForPersonalRecordsAllExercises(completedWorkout)
                                            .onErrorResume(error -> {
                                                log.warn("Personal record processing failed for workout {}: {}",
                                                        completedWorkout.getId(), error.getMessage());
                                                return Mono.empty();
                                            }))
                                    .then(Mono.just(completedWorkout));
                        })
        ).single();
    }

    // ANALYTICS AND PERSONAL RECORDS

    /**
     * Check for personal records in a specific exercise - NOW PASSES EXERCISE NAME!
     */
    private Mono<Void> checkForPersonalRecords(Workout workout, int exerciseIndex) {
        if (exerciseIndex >= workout.getExercises().size()) {
            return Mono.empty();
        }

        WorkoutExercise exercise = workout.getExercises().get(exerciseIndex);

        // IMPORTANT: Now passing exercise name to PR service!
        return personalRecordService.checkAndUpdatePersonalRecordsWithRetry(
                        workout.getUserId(),
                        exercise.getExerciseId(),
                        exercise.getExerciseName(), // This is the key fix!
                        exercise.getSets()
                )
                .then()
                .onErrorResume(error -> {
                    log.warn("Personal record check failed for workout {}, exercise '{}': {} - continuing without PR update",
                            workout.getId(), exercise.getExerciseName(), error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Check for personal records across all exercises in the workout - NOW PASSES EXERCISE NAMES!
     */
    private Mono<Void> checkForPersonalRecordsAllExercises(Workout workout) {
        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> personalRecordService.checkAndUpdatePersonalRecordsWithRetry(
                                workout.getUserId(),
                                exercise.getExerciseId(),
                                exercise.getExerciseName(), // This is the key fix!
                                exercise.getSets()
                        )
                        .onErrorResume(error -> {
                            log.warn("Personal record check failed for exercise '{}' in workout {}: {} - continuing",
                                    exercise.getExerciseName(), workout.getId(), error.getMessage());
                            return Mono.just(new ArrayList<>());
                        }))
                .then()
                .doOnSuccess(v -> log.debug("Completed personal record checks for workout: {}", workout.getId()))
                .doOnError(error -> log.warn("Some personal record checks failed for workout {}: {}",
                        workout.getId(), error.getMessage()));
    }

    // EVENT PUBLISHING

    /**
     * REFACTORED: Create WorkoutCompletedEvent using domain services for calculations
     */
    private Mono<Void> publishWorkoutCompletedEvent(Workout workout) {
        log.info("Creating WorkoutCompletedEvent using domain services");

        // NEW: Use domain services for calculations instead of model methods
        WorkoutMetrics metrics;
        List<String> muscleGroups;
        Integer calories;

        if (shouldUseNewCalculation(workout.getUserId())) {
            // NEW: Use domain calculation services
            metrics = workoutCalculationService.calculateWorkoutMetrics(workout);
            muscleGroups = workoutCalculationService.getWorkedMuscleGroups(workout);
            calories = workoutCalculationService.estimateCaloriesBurned(workout);
        } else {
            // LEGACY: Fallback to old methods (for safe migration)
            metrics = createLegacyMetrics(workout);
            muscleGroups = List.of(); // Simplified for fallback
            calories = 0;
        }

        Duration actualDuration = workoutCalculationService.calculateWorkoutDuration(workout);

        WorkoutCompletedEvent event = WorkoutCompletedEvent.builder()
                .userId(workout.getUserId())
                .workoutId(workout.getId())
                .workoutType(workout.getWorkoutType())
                .durationMinutes((int) actualDuration.toMinutes())
                .totalVolume(metrics.getTotalVolume().getValue())
                .totalSets(metrics.getTotalSets())
                .totalReps(metrics.getTotalReps())
                .exercisesCompleted(metrics.getCompletedExercises())
                .caloriesBurned(calories)
                .workoutStartTime(workout.getStartedAt() != null ?
                        workout.getStartedAt().toInstant(ZoneOffset.UTC) :
                        workout.getWorkoutDate().toInstant(ZoneOffset.UTC))
                .workoutEndTime(workout.getCompletedAt() != null ?
                        workout.getCompletedAt().toInstant(ZoneOffset.UTC) :
                        Instant.now())
                .workedMuscleGroups(muscleGroups)
                .timestamp(Instant.now())
                .build();

        return transactionalEventPublisher.publishWorkoutCompleted(event);
    }

    /**
     * NEW: Feature flag check for domain service usage
     */
    private boolean shouldUseNewCalculation(Long userId) {
        if (featureToggleService != null) {
            return featureToggleService.shouldUseNewCalculation(userId);
        }
        return useNewCalculation; // Fallback to global flag
    }

    /**
     * LEGACY: Create metrics using old approach (for safe migration)
     */
    private WorkoutMetrics createLegacyMetrics(Workout workout) {
        // Simple fallback implementation
        return WorkoutMetrics.empty();
    }

    // HELPER METHODS (unchanged, but simplified where possible)

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
        if (!combinedTags.contains("from-plan")) {
            combinedTags.add("from-plan");
        }
        return combinedTags;
    }

    private void updatePlanUsageStats(String planId, Long userId) {
        workoutPlanService.incrementPlanUsage(planId, userId)
                .subscribe(
                        success -> log.debug("Updated usage stats for plan: {}", planId),
                        error -> log.warn("Failed to update usage stats for plan {}: {}", planId, error.getMessage())
                );
    }

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

    // Customization helper methods
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

    // Remaining methods (unchanged for compatibility)
    public Mono<Workout> updateSet(String workoutId, Long userId, int exerciseIndex, int setIndex, LogSetRequest setRequest) {
        // Implementation unchanged for brevity
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    if (exerciseIndex >= workout.getExercises().size() || setIndex >= workout.getExercises().get(exerciseIndex).getSets().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid exercise or set index"));
                    }

                    WorkoutSet existingSet = workout.getExercises().get(exerciseIndex).getSets().get(setIndex);
                    updateWorkoutSetFromRequest(existingSet, setRequest);

                    return workoutRepository.save(workout);
                });
    }

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

    // Additional query methods (unchanged)
    public Mono<Long> countWorkoutsInDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return workoutRepository.countByUserIdAndWorkoutDateBetween(userId, startDate, endDate);
    }

    public Flux<Workout> findByUserAndExercise(Long userId, String exerciseId) {
        return workoutRepository.findByUserIdAndExerciseId(userId, exerciseId);
    }

    public Flux<Workout> findAll() {
        return workoutRepository.findAll();
    }

    public Flux<Workout> findByUserAndVolumeRange(Long userId, BigDecimal minVolume, BigDecimal maxVolume) {
        return workoutRepository.findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(userId, minVolume, maxVolume);
    }

    public Flux<Workout> findRecentWorkouts(Long userId) {
        return workoutRepository.findTop10ByUserIdOrderByWorkoutDateDesc(userId);
    }

    public Mono<Workout> save(Workout workout) {
        return workoutRepository.save(workout);
    }

    public Mono<Void> deleteById(String id) {
        return workoutRepository.deleteById(id);
    }

    public Mono<Workout> cancelWorkout(String workoutId, Long userId) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
                    workout.cancelWorkout();
                    return save(workout);
                });
    }

    public Mono<Void> deleteSet(String workoutId, Long userId, int exerciseIndex, int setIndex) {
        return findByIdAndUserId(workoutId, userId)
                .flatMap(workout -> {
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
}