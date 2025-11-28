package com.muscledia.workout_service.controller;


import com.muscledia.workout_service.constant.WorkoutPlanConstants;
import com.muscledia.workout_service.dto.request.AddExerciseToPlanRequest;
import com.muscledia.workout_service.dto.request.CreateWorkoutPlanRequest;
import com.muscledia.workout_service.dto.request.UpdateExerciseSetsRequest;
import com.muscledia.workout_service.dto.response.ExerciseSummaryResponse;
import com.muscledia.workout_service.mapper.ExerciseMapper;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.ExerciseService;
import com.muscledia.workout_service.service.UserWorkoutPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller for user workout plan creation
 *
 * CLEAN ARCHITECTURE:
 * - Thin controller, business logic in service
 * - Handles HTTP concerns only
 * - Delegates to service layer
 * - Uses mappers for data transformation
 *
 * ENHANCED FILTERING:
 * Now supports comprehensive exercise filtering:
 * - Category (STRENGTH, CARDIO, etc.)
 * - Body Part (chest, back, legs, etc.)
 * - Difficulty (BEGINNER, INTERMEDIATE, ADVANCED)
 * - Target Muscle (specific muscles)
 * - Equipment (barbell, dumbbell, body weight, etc.)
 */
@RestController
@RequestMapping("/api/v1/my-workout-plans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "My Workout Plans", description = "Create and manage personal workout plans")
public class MyWorkoutPlanController {

    private final UserWorkoutPlanService userWorkoutPlanService;
    private final ExerciseService exerciseService;
    private final AuthenticationService authenticationService;
    private final ExerciseMapper exerciseMapper;

    // ==================== WORKOUT PLAN CRUD ====================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create new personal workout plan",
            description = "Create a new empty workout plan template",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<WorkoutPlan> createPlan(
            @Valid @RequestBody CreateWorkoutPlanRequest request) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> userWorkoutPlanService.createPersonalPlan(userId, request));
    }

    @GetMapping
    @Operation(
            summary = "Get my workout plans",
            description = "Retrieve all personal workout plans",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Flux<WorkoutPlan> getMyPlans() {
        return authenticationService.getCurrentUserId()
                .flatMapMany(userWorkoutPlanService::getUserPlans);
    }

    @GetMapping("/{planId}")
    @Operation(
            summary = "Get single workout plan",
            description = "Retrieve a specific workout plan with all exercises",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<WorkoutPlan> getPlan(@PathVariable String planId) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> userWorkoutPlanService.getUserPlan(userId, planId));
    }

    // ==================== EXERCISE BROWSING ====================

    @GetMapping("/browse-exercises")
    @Operation(
            summary = "Browse exercises for adding to plan",
            description = """
            Browse available exercises with comprehensive filtering options.
            Used when adding exercises to a workout plan.
            
            Filters (all optional, can be combined):
            - category: Exercise type (STRENGTH, CARDIO, FLEXIBILITY, etc.)
            - bodyPart: Targeted body part (chest, back, legs, abs, etc.)
            - difficulty: Skill level (BEGINNER, INTERMEDIATE, ADVANCED)
            - targetMuscle: Specific muscle (biceps, quads, abs, etc.)
            - equipment: Required equipment (barbell, dumbbell, body weight, etc.)
            
            Examples:
            - /browse-exercises?bodyPart=chest&difficulty=BEGINNER
            - /browse-exercises?category=STRENGTH&equipment=barbell
            - /browse-exercises?targetMuscle=biceps&difficulty=INTERMEDIATE
            """
    )
    public Flux<ExerciseSummaryResponse> browseExercises(
            @Parameter(description = "Filter by exercise category", example = "STRENGTH")
            @RequestParam(required = false) ExerciseCategory category,

            @Parameter(description = "Filter by body part", example = "chest")
            @RequestParam(required = false) String bodyPart,

            @Parameter(description = "Filter by difficulty level", example = "BEGINNER")
            @RequestParam(required = false) ExerciseDifficulty difficulty,

            @Parameter(description = "Filter by target muscle", example = "biceps")
            @RequestParam(required = false) String targetMuscle,

            @Parameter(description = "Filter by equipment", example = "barbell")
            @RequestParam(required = false) String equipment,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        // Validate and limit page size
        int validatedSize = Math.min(size, WorkoutPlanConstants.MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(page, validatedSize);

        // Determine which query to execute based on filters
        return determineExerciseQuery(category, bodyPart, difficulty, targetMuscle, equipment, pageable)
                .map(exerciseMapper::toSummaryResponse);
    }

    // ==================== ADD/MODIFY EXERCISES IN PLAN ====================

    @PostMapping("/{planId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add exercise to workout plan",
            description = """
            Add an exercise from the library to your workout plan.
            The exercise will be added with default sets that you can customize.
            """,
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<WorkoutPlan> addExerciseToPlan(
            @PathVariable String planId,
            @Valid @RequestBody AddExerciseToPlanRequest request) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId ->
                        userWorkoutPlanService.addExerciseToPlan(userId, planId, request));
    }

    @PutMapping("/{planId}/exercises/{exerciseIndex}/sets")
    @Operation(
            summary = "Update sets for exercise in plan",
            description = "Modify the sets configuration for a specific exercise",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<WorkoutPlan> updateExerciseSets(
            @PathVariable String planId,
            @PathVariable Integer exerciseIndex,
            @Valid @RequestBody UpdateExerciseSetsRequest request) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId ->
                        userWorkoutPlanService.updateExerciseSets(
                                userId, planId, exerciseIndex, request.getSets()));
    }

    @DeleteMapping("/{planId}/exercises/{exerciseIndex}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove exercise from plan",
            description = "Delete an exercise from the workout plan",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<Void> removeExerciseFromPlan(
            @PathVariable String planId,
            @PathVariable Integer exerciseIndex) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId ->
                        userWorkoutPlanService.removeExerciseFromPlan(userId, planId, exerciseIndex))
                .then();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Determine which ExerciseService query to use based on provided filters
     *
     * BUSINESS LOGIC:
     * - Handles all combinations of filters
     * - Prioritizes more specific queries for better performance
     * - Falls back to general queries when needed
     */
    private Flux<Exercise> determineExerciseQuery(
            ExerciseCategory category,
            String bodyPart,
            ExerciseDifficulty difficulty,
            String targetMuscle,
            String equipment,
            PageRequest pageable) {

        // Count active filters
        int filterCount = countActiveFilters(category, bodyPart, difficulty, targetMuscle, equipment);

        // Handle combinations of 3+ filters
        if (filterCount >= 3) {
            return handleComplexFilters(category, bodyPart, difficulty, targetMuscle, equipment, pageable);
        }

        // Handle 2 filter combinations
        if (filterCount == 2) {
            return handleTwoFilters(category, bodyPart, difficulty, targetMuscle, equipment, pageable);
        }

        // Handle single filters
        if (filterCount == 1) {
            return handleSingleFilter(category, bodyPart, difficulty, targetMuscle, equipment, pageable);
        }

        // No filters - return all exercises
        return exerciseService.findAllPaginated(pageable);
    }

    /**
     * Handle queries with 3+ filters
     * For complex combinations, we start with most specific query and filter in memory
     */
    private Flux<Exercise> handleComplexFilters(
            ExerciseCategory category,
            String bodyPart,
            ExerciseDifficulty difficulty,
            String targetMuscle,
            String equipment,
            PageRequest pageable) {

        // Start with the most restrictive available combination
        Flux<Exercise> baseQuery;

        if (category != null && bodyPart != null) {
            baseQuery = exerciseService.findByCategoryAndBodyPartPaginated(category, bodyPart, pageable);
        } else if (category != null && difficulty != null) {
            baseQuery = exerciseService.findByCategoryAndDifficultyPaginated(category, difficulty, pageable);
        } else if (bodyPart != null && equipment != null) {
            baseQuery = exerciseService.findByBodyPartAndEquipmentPaginated(bodyPart, equipment, pageable);
        } else if (targetMuscle != null && equipment != null) {
            baseQuery = exerciseService.findByMuscleGroupAndEquipment(targetMuscle, equipment, pageable);
        } else if (difficulty != null) {
            baseQuery = exerciseService.findByDifficultyPaginated(difficulty, pageable);
        } else {
            baseQuery = exerciseService.findAllPaginated(pageable);
        }

        // Apply remaining filters in memory
        return baseQuery
                .filter(ex -> matchesCategory(ex, category))
                .filter(ex -> matchesBodyPart(ex, bodyPart))
                .filter(ex -> matchesDifficulty(ex, difficulty))
                .filter(ex -> matchesTargetMuscle(ex, targetMuscle))
                .filter(ex -> matchesEquipment(ex, equipment));
    }

    /**
     * Handle queries with exactly 2 filters
     */
    private Flux<Exercise> handleTwoFilters(
            ExerciseCategory category,
            String bodyPart,
            ExerciseDifficulty difficulty,
            String targetMuscle,
            String equipment,
            PageRequest pageable) {

        // Category + Body Part
        if (category != null && bodyPart != null) {
            return exerciseService.findByCategoryAndBodyPartPaginated(category, bodyPart, pageable);
        }

        // Category + Difficulty
        if (category != null && difficulty != null) {
            return exerciseService.findByCategoryAndDifficultyPaginated(category, difficulty, pageable);
        }

        // Body Part + Equipment
        if (bodyPart != null && equipment != null) {
            return exerciseService.findByBodyPartAndEquipmentPaginated(bodyPart, equipment, pageable);
        }

        // Target Muscle + Equipment
        if (targetMuscle != null && equipment != null) {
            return exerciseService.findByMuscleGroupAndEquipment(targetMuscle, equipment, pageable);
        }

        // Difficulty + Target Muscle
        if (difficulty != null && targetMuscle != null) {
            return exerciseService.findByDifficultyAndMuscle(difficulty, targetMuscle);
        }

        // For other 2-filter combinations, use single filter + in-memory filtering
        if (category != null) {
            return exerciseService.findByCategoryPaginated(category, pageable)
                    .filter(ex -> matchesBodyPart(ex, bodyPart))
                    .filter(ex -> matchesDifficulty(ex, difficulty))
                    .filter(ex -> matchesTargetMuscle(ex, targetMuscle))
                    .filter(ex -> matchesEquipment(ex, equipment));
        }

        if (bodyPart != null) {
            return exerciseService.findByBodyPartPaginated(bodyPart, pageable)
                    .filter(ex -> matchesDifficulty(ex, difficulty))
                    .filter(ex -> matchesTargetMuscle(ex, targetMuscle))
                    .filter(ex -> matchesEquipment(ex, equipment));
        }

        // Default fallback
        return exerciseService.findAllPaginated(pageable)
                .filter(ex -> matchesCategory(ex, category))
                .filter(ex -> matchesBodyPart(ex, bodyPart))
                .filter(ex -> matchesDifficulty(ex, difficulty))
                .filter(ex -> matchesTargetMuscle(ex, targetMuscle))
                .filter(ex -> matchesEquipment(ex, equipment));
    }

    /**
     * Handle queries with single filter
     */
    private Flux<Exercise> handleSingleFilter(
            ExerciseCategory category,
            String bodyPart,
            ExerciseDifficulty difficulty,
            String targetMuscle,
            String equipment,
            PageRequest pageable) {

        if (category != null) {
            return exerciseService.findByCategoryPaginated(category, pageable);
        }
        if (bodyPart != null) {
            return exerciseService.findByBodyPartPaginated(bodyPart, pageable);
        }
        if (difficulty != null) {
            return exerciseService.findByDifficultyPaginated(difficulty, pageable);
        }
        if (targetMuscle != null) {
            return exerciseService.findByMuscleGroupPaginated(targetMuscle, pageable);
        }
        if (equipment != null) {
            return exerciseService.findByEquipmentPaginated(equipment, pageable);
        }

        return exerciseService.findAllPaginated(pageable);
    }

    // ==================== FILTER MATCHING HELPERS ====================

    private int countActiveFilters(
            ExerciseCategory category,
            String bodyPart,
            ExerciseDifficulty difficulty,
            String targetMuscle,
            String equipment) {
        int count = 0;
        if (category != null) count++;
        if (bodyPart != null) count++;
        if (difficulty != null) count++;
        if (targetMuscle != null) count++;
        if (equipment != null) count++;
        return count;
    }

    private boolean matchesCategory(Exercise exercise, ExerciseCategory category) {
        return category == null || category.equals(exercise.getCategory());
    }

    private boolean matchesBodyPart(Exercise exercise, String bodyPart) {
        return bodyPart == null || bodyPart.equalsIgnoreCase(exercise.getBodyPart());
    }

    private boolean matchesDifficulty(Exercise exercise, ExerciseDifficulty difficulty) {
        return difficulty == null || difficulty.equals(exercise.getDifficulty());
    }

    private boolean matchesTargetMuscle(Exercise exercise, String targetMuscle) {
        if (targetMuscle == null) return true;
        if (exercise.getTargetMuscle() != null && exercise.getTargetMuscle().equalsIgnoreCase(targetMuscle)) {
            return true;
        }
        if (exercise.getSecondaryMuscles() != null) {
            return exercise.getSecondaryMuscles().stream()
                    .anyMatch(muscle -> muscle.equalsIgnoreCase(targetMuscle));
        }
        return false;
    }

    private boolean matchesEquipment(Exercise exercise, String equipment) {
        return equipment == null ||
                (exercise.getEquipment() != null && exercise.getEquipment().equalsIgnoreCase(equipment));
    }
}
