package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.dto.request.CompleteWorkoutRequest;
import com.muscledia.workout_service.dto.request.StartWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.AddExerciseRequest;
import com.muscledia.workout_service.dto.request.embedded.LogSetRequest;
import com.muscledia.workout_service.dto.response.WorkoutExerciseResponse;
import com.muscledia.workout_service.dto.response.WorkoutResponse;
import com.muscledia.workout_service.dto.response.WorkoutSetResponse;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.WorkoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workouts", description = "Workout management endpoints (Requires Authentication)")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final AuthenticationService authenticationService;

    // WORKOUT SESSION LIFECYCLE

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Start a new workout session",
            description = "Create and start a new workout session. This initializes the workout in IN_PROGRESS state.",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Workout session started successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WorkoutResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "400", description = "Invalid workout data")
    })
    public Mono<WorkoutResponse> startWorkout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Workout session details",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = StartWorkoutRequest.class),
                            examples = @ExampleObject(value = """
                    {
                      "workoutName": "Morning Push Session",
                      "workoutType": "STRENGTH",
                      "workoutPlanId": "507f1f77bcf86cd799439011",
                      "location": "Home Gym",
                      "notes": "Feeling strong today",
                      "tags": ["morning", "push", "chest"]
                    }
                    """)))
            @Valid @RequestBody StartWorkoutRequest request) {

        log.info("Starting new workout session: {}", request.getWorkoutName());

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.startWorkout(userId, request))
                .map(this::convertToResponse)
                .doOnSuccess(response -> log.info("Successfully started workout session with ID: {}",
                        response.getId()));
    }

    @GetMapping("/{workoutId}")
    @Operation(
            summary = "Get workout session details",
            description = "Retrieve complete details of a workout session including all exercises and sets",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workout found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WorkoutResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workout not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - not your workout")
    })
    public Mono<ResponseEntity<WorkoutResponse>> getWorkout(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.findByIdAndUserId(workoutId, userId))
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    @Operation(
            summary = "Get user's workout history",
            description = "Retrieve workout history with optional date filtering",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workouts retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = WorkoutResponse.class))))
    })
    public Flux<WorkoutResponse> getUserWorkouts(
            @Parameter(description = "Start date filter (YYYY-MM-DD)", example = "2024-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter (YYYY-MM-DD)", example = "2024-12-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return authenticationService.getCurrentUserId()
                .flatMapMany(userId -> workoutService.getUserWorkouts(userId, startDate, endDate))
                .map(this::convertToResponse);
    }

    // EXERCISE MANAGEMENT

    @PostMapping("/{workoutId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add exercise to active workout",
            description = "Add a new exercise to an active workout session",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Exercise added successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WorkoutResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid exercise data or workout not active"),
            @ApiResponse(responseCode = "404", description = "Workout not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public Mono<WorkoutResponse> addExerciseToWorkout(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Exercise to add",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AddExerciseRequest.class),
                            examples = @ExampleObject(value = """
                    {
                      "exerciseId": "507f1f77bcf86cd799439012",
                      "exerciseName": "Bench Press",
                      "exerciseCategory": "STRENGTH",
                      "primaryMuscleGroup": "chest",
                      "secondaryMuscleGroups": ["shoulders", "triceps"],
                      "equipment": "barbell",
                      "notes": "Focus on form"
                    }
                    """)))
            @Valid @RequestBody AddExerciseRequest request) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    WorkoutExercise exercise = convertToWorkoutExercise(request);
                    return workoutService.addExerciseToWorkout(workoutId, userId, exercise);
                })
                .map(this::convertToResponse);
    }

    // SET LOGGING - Core Implementation of the User Story

    @PostMapping("/{workoutId}/exercises/{exerciseIndex}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Log a new set for an exercise - CORE USER STORY IMPLEMENTATION",
            description = """
            Record the actual weight, reps, and duration for each set performed.
            This is the core implementation of the "Log Exercise Sets" user story.
            Creates a WorkoutSet object with precise performance data (e.g., 50kg, 10 reps).
            """,
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Set logged successfully - WorkoutSet object created with correct values",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WorkoutResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workout or exercise not found"),
            @ApiResponse(responseCode = "400", description = "Invalid set data or workout not active"),
            @ApiResponse(responseCode = "403", description = "Access denied - not your workout")
    })
    public Mono<WorkoutResponse> logSet(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId,
            @Parameter(description = "Exercise index (0-based)", example = "0")
            @PathVariable int exerciseIndex,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Set performance data - the granular data for each set (e.g., weightKg, reps)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LogSetRequest.class),
                            examples = @ExampleObject(value = """
                    {
                      "weightKg": 50.0,
                      "reps": 10,
                      "restSeconds": 90,
                      "rpe": 7,
                      "completed": true,
                      "setType": "WORKING",
                      "notes": "Felt strong today"
                    }
                    """)))
            @Valid @RequestBody LogSetRequest setRequest) {

        log.info("Logging set for workout {} exercise {}: {}kg x {} reps - IMPLEMENTING USER STORY",
                workoutId, exerciseIndex, setRequest.getWeightKg(), setRequest.getReps());

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.logSet(workoutId, userId, exerciseIndex, setRequest))
                .map(this::convertToResponse)
                .doOnSuccess(response -> log.info("Successfully logged set - WorkoutSet object created with correct values for workout {}", workoutId));
    }

    @PutMapping("/{workoutId}/exercises/{exerciseIndex}/sets/{setIndex}")
    @Operation(
            summary = "Update an existing set",
            description = "Modify the performance data for an existing set",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Set updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WorkoutResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workout, exercise, or set not found"),
            @ApiResponse(responseCode = "400", description = "Invalid set data"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public Mono<ResponseEntity<WorkoutResponse>> updateSet(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId,
            @Parameter(description = "Exercise index (0-based)", example = "0")
            @PathVariable int exerciseIndex,
            @Parameter(description = "Set index (0-based)", example = "0")
            @PathVariable int setIndex,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated set performance data",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LogSetRequest.class)))
            @Valid @RequestBody LogSetRequest setRequest) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.updateSet(workoutId, userId, exerciseIndex, setIndex, setRequest))
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{workoutId}/exercises/{exerciseIndex}/sets/{setIndex}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a set",
            description = "Remove a set from an exercise",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Set deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Workout, exercise, or set not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public Mono<ResponseEntity<Void>> deleteSet(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId,
            @Parameter(description = "Exercise index (0-based)", example = "0")
            @PathVariable int exerciseIndex,
            @Parameter(description = "Set index (0-based)", example = "0")
            @PathVariable int setIndex) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.deleteSet(workoutId, userId, exerciseIndex, setIndex))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    // WORKOUT COMPLETION

    @PutMapping("/{workoutId}/complete")
    @Operation(
            summary = "Complete a workout session",
            description = "Mark workout as completed, calculate final metrics, and publish events for gamification",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Workout completed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = WorkoutResponse.class))),
            @ApiResponse(responseCode = "404", description = "Workout not found"),
            @ApiResponse(responseCode = "400", description = "Workout already completed or invalid state"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public Mono<ResponseEntity<WorkoutResponse>> completeWorkout(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Optional completion data",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CompleteWorkoutRequest.class),
                            examples = @ExampleObject(value = """
                    {
                      "rating": 8,
                      "notes": "Great workout, felt strong throughout",
                      "caloriesBurned": 450,
                      "additionalTags": ["personal-record"]
                    }
                    """)))
            @RequestBody(required = false) CompleteWorkoutRequest completionRequest) {

        log.info("Completing workout session: {}", workoutId);

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    Map<String, Object> completionData = convertCompletionRequest(completionRequest);
                    return workoutService.completeWorkout(workoutId, userId, completionData);
                })
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(response -> log.info("Successfully completed workout: {}", workoutId));
    }

    @PutMapping("/{workoutId}/cancel")
    @Operation(
            summary = "Cancel a workout session",
            description = "Cancel an active workout session",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<ResponseEntity<WorkoutResponse>> cancelWorkout(
            @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011")
            @PathVariable String workoutId) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.cancelWorkout(workoutId, userId))
                .map(this::convertToResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    // ANALYTICS ENDPOINTS

    @GetMapping("/analytics/recent")
    @Operation(
            summary = "Get recent workout analytics",
            description = "Get performance analytics for recent workouts",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Flux<WorkoutResponse> getRecentWorkouts() {
        return authenticationService.getCurrentUserId()
                .flatMapMany(workoutService::findRecentWorkouts)
                .map(this::convertToResponse);
    }

    @GetMapping("/analytics/volume")
    @Operation(
            summary = "Get workouts by volume range",
            description = "Find workouts within a specific volume range",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Flux<WorkoutResponse> getWorkoutsByVolume(
            @Parameter(description = "Minimum volume", example = "1000")
            @RequestParam java.math.BigDecimal minVolume,
            @Parameter(description = "Maximum volume", example = "5000")
            @RequestParam java.math.BigDecimal maxVolume) {

        return authenticationService.getCurrentUserId()
                .flatMapMany(userId -> workoutService.findByUserAndVolumeRange(userId, minVolume, maxVolume))
                .map(this::convertToResponse);
    }

    // HELPER METHODS - FIXED NULL SAFETY

    /**
     * FIXED: Convert Workout entity to WorkoutResponse DTO with null safety
     */
    private WorkoutResponse convertToResponse(Workout workout) {
        return WorkoutResponse.builder()
                .id(workout.getId())
                .userId(workout.getUserId())
                .workoutName(workout.getWorkoutName())
                .workoutPlanId(workout.getWorkoutPlanId())
                .workoutType(workout.getWorkoutType())
                .status(workout.getStatus() != null ? workout.getStatus().toString() : "UNKNOWN")
                .startedAt(workout.getStartedAt() != null ? workout.getStartedAt().toString() : null)
                .completedAt(workout.getCompletedAt() != null ? workout.getCompletedAt().toString() : null)
                .durationMinutes(workout.getDurationMinutes())
                .exercises(convertExercisesToResponse(workout.getExercises()))
                .metrics(WorkoutResponse.WorkoutMetrics.builder()
                        .totalVolume(workout.getTotalVolume())
                        .totalSets(workout.getTotalSets())
                        .totalReps(workout.getTotalReps())
                        .caloriesBurned(workout.getCaloriesBurned())
                        .workedMuscleGroups(workout.getWorkedMuscleGroups())
                        .build())
                .context(WorkoutResponse.WorkoutContext.builder()
                        .location(workout.getLocation())
                        .notes(workout.getNotes())
                        .rating(workout.getRating())
                        .tags(workout.getTags() != null ? workout.getTags() : new ArrayList<>())
                        .build())
                .build();
    }

    private List<WorkoutExerciseResponse> convertExercisesToResponse(List<WorkoutExercise> exercises) {
        if (exercises == null) {
            return new ArrayList<>();
        }
        return exercises.stream()
                .map(this::convertExerciseToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    private WorkoutExerciseResponse convertExerciseToResponse(WorkoutExercise exercise) {
        return WorkoutExerciseResponse.builder()
                .exerciseId(exercise.getExerciseId())
                .exerciseName(exercise.getExerciseName())
                .exerciseOrder(exercise.getExerciseOrder())
                .exerciseCategory(exercise.getExerciseCategory())
                .primaryMuscleGroup(exercise.getPrimaryMuscleGroup())
                .secondaryMuscleGroups(exercise.getSecondaryMuscleGroups() != null ?
                        exercise.getSecondaryMuscleGroups() : new ArrayList<>())
                .equipment(exercise.getEquipment())
                .sets(convertSetsToResponse(exercise.getSets()))
                .notes(exercise.getNotes())
                .startedAt(exercise.getStartedAt() != null ? exercise.getStartedAt().toString() : null)
                .completedAt(exercise.getCompletedAt() != null ? exercise.getCompletedAt().toString() : null)
                .totalVolume(exercise.getTotalVolume())
                .totalReps(exercise.getTotalReps())
                .maxWeight(exercise.getMaxWeight())
                .averageRpe(exercise.getAverageRpe())
                .completedSets(exercise.getCompletedSetsCount())
                .build();
    }

    private List<WorkoutSetResponse> convertSetsToResponse(List<WorkoutSet> sets) {
        if (sets == null) {
            return new ArrayList<>();
        }
        return sets.stream()
                .map(this::convertSetToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    private WorkoutSetResponse convertSetToResponse(WorkoutSet set) {
        return WorkoutSetResponse.builder()
                .setNumber(set.getSetNumber())
                .weightKg(set.getWeightKg())
                .reps(set.getReps())
                .durationSeconds(set.getDurationSeconds())
                .distanceMeters(set.getDistanceMeters())
                .restSeconds(set.getRestSeconds())
                .rpe(set.getRpe())
                .completed(set.getCompleted())
                .failure(set.getFailure())
                .dropSet(set.getDropSet())
                .warmUp(set.getWarmUp())
                .setType(set.getSetType())
                .notes(set.getNotes())
                .startedAt(set.getStartedAt() != null ? set.getStartedAt().toString() : null)
                .completedAt(set.getCompletedAt() != null ? set.getCompletedAt().toString() : null)
                .volume(set.getVolume())
                .build();
    }

    private WorkoutExercise convertToWorkoutExercise(AddExerciseRequest request) {
        return WorkoutExercise.builder()
                .exerciseId(request.getExerciseId())
                .exerciseName(request.getExerciseName())
                .exerciseCategory(request.getExerciseCategory())
                .primaryMuscleGroup(request.getPrimaryMuscleGroup())
                .secondaryMuscleGroups(request.getSecondaryMuscleGroups() != null ?
                        request.getSecondaryMuscleGroups() : new ArrayList<>())
                .equipment(request.getEquipment())
                .notes(request.getNotes())
                .sets(new ArrayList<>()) // Initialize empty sets list
                .build();
    }

    private Map<String, Object> convertCompletionRequest(CompleteWorkoutRequest request) {
        if (request == null) return new HashMap<>();

        Map<String, Object> data = new HashMap<>();
        if (request.getRating() != null) data.put("rating", request.getRating());
        if (request.getNotes() != null) data.put("notes", request.getNotes());
        if (request.getCaloriesBurned() != null) data.put("caloriesBurned", request.getCaloriesBurned());
        if (request.getAdditionalTags() != null) data.put("additionalTags", request.getAdditionalTags());

        return data;
    }
}