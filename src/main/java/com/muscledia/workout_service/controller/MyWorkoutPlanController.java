package com.muscledia.workout_service.controller;


import com.muscledia.workout_service.dto.request.AddExerciseToPlanRequest;
import com.muscledia.workout_service.dto.request.CreateWorkoutPlanRequest;
import com.muscledia.workout_service.dto.request.UpdateExerciseSetsRequest;
import com.muscledia.workout_service.dto.response.ExerciseSummaryResponse;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.WorkoutPlan;
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
            Browse available exercises with filtering options.
            Used when adding exercises to a workout plan.
            
            Filters:
            - muscleGroup: Filter by primary or secondary muscle group
            - equipment: Filter by required equipment
            - Supports pagination for large result sets
            """
    )
    public Flux<ExerciseSummaryResponse> browseExercises(
            @Parameter(description = "Filter by muscle group", example = "chest")
            @RequestParam(required = false) String muscleGroup,

            @Parameter(description = "Filter by equipment", example = "barbell")
            @RequestParam(required = false) String equipment,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);

        Flux<Exercise> exercises;

        // Apply filters based on parameters
        if (muscleGroup != null && equipment != null) {
            exercises = exerciseService.findByMuscleGroupAndEquipment(muscleGroup, equipment, pageable);
        } else if (muscleGroup != null) {
            exercises = exerciseService.findByMuscleGroupPaginated(muscleGroup, pageable);
        } else if (equipment != null) {
            exercises = exerciseService.findByEquipmentPaginated(equipment, pageable);
        } else {
            exercises = exerciseService.findAllPaginated(pageable);
        }

        return exercises.map(this::convertToSummary);
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

    // ==================== HELPER METHODS ====================

    private ExerciseSummaryResponse convertToSummary(
            com.muscledia.workout_service.model.Exercise exercise) {
        return ExerciseSummaryResponse.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .equipment(exercise.getEquipment())
                .targetMuscle(exercise.getTargetMuscle())
                .difficulty(exercise.getDifficulty())
                .animationUrl(exercise.getAnimationUrl())
                .muscleGroups(exercise.getMuscleGroups())
                .build();
    }
}
