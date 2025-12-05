package com.muscledia.workout_service.service;

import com.muscledia.workout_service.dto.request.AddExerciseToPlanRequest;
import com.muscledia.workout_service.exception.ResourceNotFoundException;
import com.muscledia.workout_service.exception.SomeDuplicateEntryException;
import com.muscledia.workout_service.exception.ValidationException;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.embedded.PlannedExercise;
import com.muscledia.workout_service.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Service for WorkoutPlan operations
 *
 * BUSINESS LOGIC LAYER:
 * - Validates inputs
 * - Handles error cases
 * - Enforces business rules
 * - Provides clean interface to controllers
 *
 * SEPARATION OF CONCERNS:
 * - Business logic stays here
 * - Controllers handle HTTP concerns
 * - Repository handles data access
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutPlanService {
    private final WorkoutPlanRepository workoutPlanRepository;
    private final ExerciseService exerciseService;

    // ==================== BASIC CRUD OPERATIONS ====================

    public Mono<WorkoutPlan> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.error(new ValidationException("id", "Workout plan ID cannot be null or empty"));
        }
        return workoutPlanRepository.findById(id)
                .doOnNext(plan -> log.debug("Found workout plan: {}", plan.getTitle()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("WorkoutPlan", "id", id)));
    }

    public Mono<WorkoutPlan> save(WorkoutPlan workoutPlan) {
        if (workoutPlan == null) {
            return Mono.error(new ValidationException("workoutPlan", "Workout plan cannot be null"));
        }
        if (workoutPlan.getTitle() == null || workoutPlan.getTitle().trim().isEmpty()) {
            return Mono.error(new ValidationException("title", "Workout plan title cannot be null or empty"));
        }

        if (workoutPlan.getCreatedAt() == null) {
            workoutPlan.setCreatedAt(LocalDateTime.now());
        }
        workoutPlan.setUpdatedAt(LocalDateTime.now());

        if (workoutPlan.getUsageCount() == null) {
            workoutPlan.setUsageCount(0L);
        }

        return workoutPlanRepository.save(workoutPlan)
                .doOnSuccess(saved -> log.debug("Saved workout plan: {}", saved.getTitle()));
    }

    public Mono<WorkoutPlan> update(String id, WorkoutPlan workoutPlan) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.error(new ValidationException("id", "Workout plan ID cannot be null or empty"));
        }
        if (workoutPlan == null) {
            return Mono.error(new ValidationException("workoutPlan", "Workout plan cannot be null"));
        }

        return findById(id)
                .flatMap(existing -> {
                    // PARTIAL UPDATE LOGIC: Only update fields if they are provided (not null)

                    if (workoutPlan.getTitle() != null) {
                        existing.setTitle(workoutPlan.getTitle());
                    }

                    if (workoutPlan.getDescription() != null) {
                        existing.setDescription(workoutPlan.getDescription());
                    }

                    // Crucial: Only update exercises if the list is explicitly provided
                    // This allows sending { title: "New" } without wiping exercises
                    if (workoutPlan.getExercises() != null) {
                        existing.setExercises(workoutPlan.getExercises());
                    }

                    if (workoutPlan.getEstimatedDurationMinutes() != null) {
                        existing.setEstimatedDurationMinutes(workoutPlan.getEstimatedDurationMinutes());
                    }

                    if (workoutPlan.getIsPublic() != null) {
                        existing.setIsPublic(workoutPlan.getIsPublic());
                    }

                    if (workoutPlan.getFolderId() != null) {
                        existing.setFolderId(workoutPlan.getFolderId());
                    }

                    // Always update system fields
                    existing.setUpdatedAt(LocalDateTime.now());

                    return workoutPlanRepository.save(existing);
                })
                .doOnSuccess(updated -> log.debug("Updated workout plan (partial): {}", updated.getTitle()));
    }

    public Mono<Void> deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.error(new ValidationException("id", "Workout plan ID cannot be null or empty"));
        }
        return findById(id)
                .then(workoutPlanRepository.deleteById(id))
                .doOnSuccess(v -> log.debug("Deleted workout plan with id: {}", id));
    }

    public Flux<WorkoutPlan> findAll() {
        return workoutPlanRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all workout plans"));
    }

    // ==================== PUBLIC WORKOUT PLANS ====================

    public Flux<WorkoutPlan> findPublicWorkoutPlans() {
        return workoutPlanRepository.findByIsPublicTrue()
                .doOnComplete(() -> log.debug("Retrieved public workout plans"));
    }

    public Flux<WorkoutPlan> searchPublicWorkouts(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return Flux.error(new ValidationException("searchTerm", "Search term cannot be null or empty"));
        }
        return workoutPlanRepository.searchPublicWorkouts(searchTerm)
                .doOnComplete(() -> log.debug("Searched public workouts with term: {}", searchTerm));
    }

    public Flux<WorkoutPlan> findPopularWorkoutPlans(Pageable pageable) {
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return workoutPlanRepository.findByIsPublicTrueOrderByUsageCountDesc(pageable)
                .doOnComplete(() -> log.debug("Retrieved popular workout plans"));
    }

    public Flux<WorkoutPlan> findRecentWorkoutPlans(Pageable pageable) {
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return workoutPlanRepository.findByIsPublicTrueOrderByCreatedAtDesc(pageable)
                .doOnComplete(() -> log.debug("Retrieved recent workout plans"));
    }

    // ==================== PERSONAL COLLECTION OPERATIONS ====================

    public Flux<WorkoutPlan> findPersonalWorkoutPlans(Long userId) {
        if (userId == null) {
            return Flux.error(new ValidationException("userId", "User ID cannot be null"));
        }
        return workoutPlanRepository.findByCreatedByAndIsPublicFalse(userId)
                .doOnComplete(() -> log.debug("Retrieved personal workout plans for user: {}", userId));
    }

    public Flux<WorkoutPlan> findByCreator(Long userId) {
        if (userId == null) {
            return Flux.error(new ValidationException("userId", "User ID cannot be null"));
        }
        return workoutPlanRepository.findByCreatedByOrderByCreatedAtDesc(userId)
                .doOnComplete(() -> log.debug("Retrieved all workout plans created by user: {}", userId));
    }

    public Mono<WorkoutPlan> saveToPersonalCollection(String publicPlanId, Long userId) {
        if (publicPlanId == null || publicPlanId.trim().isEmpty()) {
            return Mono.error(new ValidationException("publicPlanId", "Public plan ID cannot be null or empty"));
        }
        if (userId == null) {
            return Mono.error(new ValidationException("userId", "User ID cannot be null"));
        }

        log.info("Saving public workout plan {} to personal collection for user {}", publicPlanId, userId);

        return findById(publicPlanId)
                .filter(WorkoutPlan::getIsPublic)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Public WorkoutPlan", "id", publicPlanId)))
                .flatMap(publicPlan -> checkForDuplicatePlan(publicPlan.getTitle(), userId)
                        .then(Mono.defer(() -> createPersonalCopy(publicPlan, userId))))
                .doOnSuccess(saved -> log.info("Successfully saved workout plan '{}' to personal collection",
                        saved.getTitle()));
    }

    private Mono<Void> checkForDuplicatePlan(String title, Long userId) {
        return workoutPlanRepository.findByCreatedByAndIsPublicFalse(userId)
                .filter(plan -> plan.getTitle().equals(title))
                .hasElements()
                .flatMap(exists -> exists
                        ? Mono.error(new SomeDuplicateEntryException(
                        String.format("A personal workout plan with title '%s' already exists", title)))
                        : Mono.empty());
    }

    private Mono<WorkoutPlan> createPersonalCopy(WorkoutPlan publicPlan, Long userId) {
        WorkoutPlan personalPlan = new WorkoutPlan();
        personalPlan.setTitle(publicPlan.getTitle());
        personalPlan.setDescription(publicPlan.getDescription());
        personalPlan.setExercises(publicPlan.getExercises());
        personalPlan.setEstimatedDurationMinutes(publicPlan.getEstimatedDurationMinutes());
        personalPlan.setIsPublic(false);
        personalPlan.setCreatedBy(userId);
        personalPlan.setFolderId(null);
        personalPlan.setUsageCount(0L);
        personalPlan.setCreatedAt(LocalDateTime.now());
        personalPlan.setUpdatedAt(LocalDateTime.now());

        publicPlan.setUsageCount(publicPlan.getUsageCount() + 1);

        return workoutPlanRepository.save(publicPlan)
                .then(workoutPlanRepository.save(personalPlan))
                .doOnSuccess(saved -> log.debug("Created personal copy of workout plan: '{}'", saved.getTitle()));
    }

    // ==================== GRANULAR EXERCISE MANAGEMENT ====================
    // Moved from UserWorkoutPlanService

    public Mono<WorkoutPlan> addExerciseToPlan(String planId, Long userId, AddExerciseToPlanRequest request) {
        return findByIdAndValidateOwnership(planId, userId)
                .flatMap(plan -> exerciseService.findById(request.getExerciseId())
                        .map(exercise -> {
                            // Map exercise to embedded format
                            PlannedExercise planned = new PlannedExercise();
                            planned.setExerciseTemplateId(exercise.getId());
                            planned.setTitle(exercise.getName());
                            // ... map other fields
                            return planned;
                        })
                        .flatMap(planned -> {
                            if (plan.getExercises() == null) plan.setExercises(new ArrayList<>());
                            planned.setIndex(plan.getExercises().size());
                            plan.getExercises().add(planned);

                            // Recalculate metadata
                            // plan.calculateDuration();
                            plan.setUpdatedAt(LocalDateTime.now());

                            return workoutPlanRepository.save(plan);
                        }));
    }

    public Mono<WorkoutPlan> removeExerciseFromPlan(String planId, Long userId, int exerciseIndex) {
        return findByIdAndValidateOwnership(planId, userId)
                .flatMap(plan -> {
                    if (plan.getExercises() == null || exerciseIndex >= plan.getExercises().size()) {
                        return Mono.error(new IllegalArgumentException("Invalid index"));
                    }

                    plan.getExercises().remove(exerciseIndex);

                    // Re-index remaining exercises
                    for(int i=0; i<plan.getExercises().size(); i++) {
                        plan.getExercises().get(i).setIndex(i);
                    }

                    plan.setUpdatedAt(LocalDateTime.now());
                    return workoutPlanRepository.save(plan);
                });
    }


    // ==================== FILTERING OPERATIONS ====================

    public Flux<WorkoutPlan> findByTargetMuscleGroup(String muscleGroup) {
        if (muscleGroup == null || muscleGroup.trim().isEmpty()) {
            return Flux.error(new ValidationException("muscleGroup", "Muscle group cannot be null or empty"));
        }
        return workoutPlanRepository.findByTargetMuscleGroup(muscleGroup)
                .doOnComplete(() -> log.debug("Retrieved workout plans for muscle group: {}", muscleGroup));
    }

    public Flux<WorkoutPlan> findByRequiredEquipment(String equipment) {
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        return workoutPlanRepository.findByRequiredEquipment(equipment)
                .doOnComplete(() -> log.debug("Retrieved workout plans for equipment: {}", equipment));
    }

    public Flux<WorkoutPlan> findByDurationRange(Integer minDuration, Integer maxDuration) {
        if (minDuration == null || maxDuration == null) {
            return Flux.error(new ValidationException("duration", "Duration range cannot be null"));
        }
        if (minDuration < 0 || maxDuration < 0) {
            return Flux.error(new ValidationException("duration", "Duration must be positive"));
        }
        if (minDuration > maxDuration) {
            return Flux.error(new ValidationException("duration", "Min duration cannot exceed max duration"));
        }

        return workoutPlanRepository.findByEstimatedDurationMinutesBetween(minDuration, maxDuration)
                .doOnComplete(() -> log.debug("Retrieved workout plans for duration range: {}-{} minutes",
                        minDuration, maxDuration));
    }

    public Flux<WorkoutPlan> findByExerciseId(String exerciseId) {
        if (exerciseId == null || exerciseId.trim().isEmpty()) {
            return Flux.error(new ValidationException("exerciseId", "Exercise ID cannot be null or empty"));
        }
        return workoutPlanRepository.findByExerciseTemplateId(exerciseId)
                .doOnComplete(() -> log.debug("Retrieved workout plans containing exercise: {}", exerciseId));
    }

    // ==================== FOLDER OPERATIONS ====================

    public Flux<WorkoutPlan> findByFolderId(String folderId) {
        if (folderId == null || folderId.trim().isEmpty()) {
            return Flux.error(new ValidationException("folderId", "Folder ID cannot be null or empty"));
        }
        return workoutPlanRepository.findByFolderId(folderId)
                .doOnComplete(() -> log.debug("Retrieved workout plans for folder: {}", folderId));
    }

    public Flux<WorkoutPlan> findPublicPlansByFolder(String folderId) {
        if (folderId == null || folderId.trim().isEmpty()) {
            return Flux.error(new ValidationException("folderId", "Folder ID cannot be null or empty"));
        }
        return workoutPlanRepository.findByFolderIdAndIsPublicTrue(folderId)
                .doOnComplete(() -> log.debug("Retrieved public workout plans for folder: {}", folderId));
    }

    public Flux<WorkoutPlan> findPersonalPlansByFolder(String folderId) {
        if (folderId == null || folderId.trim().isEmpty()) {
            return Flux.error(new ValidationException("folderId", "Folder ID cannot be null or empty"));
        }
        return workoutPlanRepository.findByFolderIdAndIsPublicFalse(folderId)
                .doOnComplete(() -> log.debug("Retrieved personal workout plans for folder: {}", folderId));
    }

    // ==================== USAGE TRACKING ====================

    public Mono<WorkoutPlan> incrementUsageCount(String planId) {
        if (planId == null || planId.trim().isEmpty()) {
            return Mono.error(new ValidationException("planId", "Plan ID cannot be null or empty"));
        }

        return findById(planId)
                .flatMap(plan -> {
                    plan.setUsageCount(plan.getUsageCount() + 1);
                    plan.setUpdatedAt(LocalDateTime.now());
                    return workoutPlanRepository.save(plan);
                })
                .doOnSuccess(plan -> log.debug("Incremented usage count for plan: {}", plan.getTitle()));
    }

    public Flux<WorkoutPlan> findRecentlyUsedPlans(Long userId, int limit) {
        if (userId == null) {
            return Flux.error(new ValidationException("userId", "User ID cannot be null"));
        }
        if (limit <= 0) {
            return Flux.error(new ValidationException("limit", "Limit must be positive"));
        }

        return workoutPlanRepository.findByCreatedByOrderByCreatedAtDesc(userId)
                .take(limit)
                .doOnComplete(() -> log.debug("Retrieved {} recently used plans for user {}", limit, userId));
    }

    // ==================== OWNERSHIP VALIDATION ====================

    public Mono<Boolean> isOwner(String planId, Long userId) {
        if (planId == null || planId.trim().isEmpty()) {
            return Mono.error(new ValidationException("planId", "Plan ID cannot be null or empty"));
        }
        if (userId == null) {
            return Mono.error(new ValidationException("userId", "User ID cannot be null"));
        }

        return workoutPlanRepository.existsByIdAndCreatedBy(planId, userId)
                .doOnSuccess(isOwner -> log.debug("User {} ownership of plan {}: {}",
                        userId, planId, isOwner));
    }

    public Mono<WorkoutPlan> findByIdAndValidateOwnership(String planId, Long userId) {
        return findById(planId)
                .flatMap(plan -> {
                    if (!userId.equals(plan.getCreatedBy())) {
                        return Mono.error(new SecurityException("Not authorized to access this workout plan"));
                    }
                    return Mono.just(plan);
                });
    }

    /**
     * Check if user has saved a specific plan to their personal collection
     * Business logic: Validates if a user has access to a plan via personal collection
     */
    public Mono<Boolean> hasUserSavedPlan(Long userId, String planId) {
        if (userId == null) {
            return Mono.error(new ValidationException("userId", "User ID cannot be null"));
        }
        if (planId == null || planId.trim().isEmpty()) {
            return Mono.error(new ValidationException("planId", "Plan ID cannot be null or empty"));
        }

        return workoutPlanRepository.findByCreatedByAndIsPublicFalse(userId)
                .filter(plan -> plan.getId().equals(planId))
                .hasElements()
                .doOnSuccess(hasSaved -> log.debug("User {} has saved plan {}: {}",
                        userId, planId, hasSaved));
    }

    // ==================== SEARCH OPERATIONS ====================

    public Flux<WorkoutPlan> searchByTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return Flux.error(new ValidationException("title", "Title cannot be null or empty"));
        }
        return workoutPlanRepository.findByTitleContainingIgnoreCase(title)
                .doOnComplete(() -> log.debug("Searched workout plans by title: {}", title));
    }

    public Flux<WorkoutPlan> findByDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return Flux.error(new ValidationException("dateRange", "Date range cannot be null"));
        }
        if (start.isAfter(end)) {
            return Flux.error(new ValidationException("dateRange", "Start date must be before end date"));
        }

        return workoutPlanRepository.findByCreatedAtBetween(start, end)
                .doOnComplete(() -> log.debug("Retrieved workout plans created between {} and {}", start, end));
    }
}