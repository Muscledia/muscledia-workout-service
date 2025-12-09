package com.muscledia.workout_service.controller;


import com.muscledia.workout_service.dto.request.CompleteWorkoutRequest;
import com.muscledia.workout_service.dto.request.StartWorkoutFromPlanRequest;
import com.muscledia.workout_service.dto.request.StartWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.AddExerciseRequest;
import com.muscledia.workout_service.dto.request.embedded.LogSetRequest;
import com.muscledia.workout_service.dto.response.*;
import com.muscledia.workout_service.mapper.ExerciseMapper;
import com.muscledia.workout_service.mapper.WorkoutMapper;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.WorkoutPlanService;
import com.muscledia.workout_service.service.WorkoutService;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workouts", description = "Workout management endpoints (Requires Authentication)")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final AuthenticationService authenticationService;
    private final WorkoutPlanService workoutPlanService;

    // NEW: Inject Mappers
    private final WorkoutMapper workoutMapper;
    private final ExerciseMapper exerciseMapper;

    // ==================== WORKOUT SESSION LIFECYCLE ====================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new workout session", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<WorkoutResponse> startWorkout(@Valid @RequestBody StartWorkoutRequest request) {
        log.info("Starting new workout session: {} (fromPlan: {})", request.getWorkoutName(), request.getWorkoutPlanId() != null);
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.startWorkout(userId, request))
                .map(workoutMapper::toResponse) // Clean!
                .doOnSuccess(response -> log.info("Started workout session: {}", response.getId()));
    }

    @PostMapping("/from-plan/{planId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start workout from saved plan", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<WorkoutResponse> startWorkoutFromPlan(@PathVariable String planId, @Valid @RequestBody(required = false) StartWorkoutFromPlanRequest planRequest) {
        log.info("Starting workout from plan: {}", planId);
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    // Logic to build request from plan request (same as before)
                    StartWorkoutRequest workoutRequest = StartWorkoutRequest.builder()
                            .workoutPlanId(planId)
                            .useWorkoutPlan(true)
                            .workoutName(planRequest != null ? planRequest.getWorkoutName() : null)
                            // ... other fields mapping ...
                            .build();
                    return workoutService.startWorkout(userId, workoutRequest);
                })
                .map(workoutMapper::toResponse);
    }

    // ==================== GET WORKOUTS ====================

    @GetMapping("/{workoutId}")
    @Operation(summary = "Get workout session details", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<WorkoutResponse>> getWorkout(@PathVariable String workoutId) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.findByIdAndUserId(workoutId, userId))
                .map(workoutMapper::toResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    @Operation(summary = "Get user's workout history", security = @SecurityRequirement(name = "bearer-key"))
    public Flux<WorkoutResponse> getUserWorkouts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return authenticationService.getCurrentUserId()
                .flatMapMany(userId -> workoutService.getUserWorkouts(userId,
                        startDate != null ? startDate.atStartOfDay() : null,
                        endDate != null ? endDate.atStartOfDay() : null))
                .map(workoutMapper::toResponse);
    }

    // ==================== EXERCISE MANAGEMENT ====================

    @PostMapping("/{workoutId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add exercise to active workout", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<WorkoutResponse> addExerciseToWorkout(@PathVariable String workoutId, @Valid @RequestBody AddExerciseRequest request) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    WorkoutExercise exercise = workoutMapper.toEntity(request); // Use Mapper
                    return workoutService.addExerciseToWorkout(workoutId, userId, exercise);
                })
                .map(workoutMapper::toResponse);
    }

    @PatchMapping("/{workoutId}/exercises/{exerciseIndex}")
    @Operation(summary = "Update exercise details (notes, etc)", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<WorkoutResponse>> updateExercise(
            @PathVariable String workoutId,
            @PathVariable int exerciseIndex,
            @RequestBody WorkoutExercise updateRequest) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.updateExerciseInWorkout(workoutId, userId, exerciseIndex, updateRequest))
                .map(workoutMapper::toResponse)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{workoutId}/exercises/{exerciseIndex}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove exercise from active workout", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<Void>> removeExercise(
            @PathVariable String workoutId,
            @PathVariable int exerciseIndex) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.removeExerciseFromWorkout(workoutId, userId, exerciseIndex))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // ==================== SET LOGGING ====================

    @PostMapping("/{workoutId}/exercises/{exerciseIndex}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a new set", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<WorkoutResponse> logSet(@PathVariable String workoutId, @PathVariable int exerciseIndex, @Valid @RequestBody LogSetRequest setRequest) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.logSet(workoutId, userId, exerciseIndex, setRequest))
                .map(workoutMapper::toResponse);
    }

    @PutMapping("/{workoutId}/exercises/{exerciseIndex}/sets/{setIndex}")
    @Operation(summary = "Update an existing set", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<WorkoutResponse>> updateSet(@PathVariable String workoutId, @PathVariable int exerciseIndex, @PathVariable int setIndex, @Valid @RequestBody LogSetRequest setRequest) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.updateSet(workoutId, userId, exerciseIndex, setIndex, setRequest))
                .map(workoutMapper::toResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{workoutId}/exercises/{exerciseIndex}/sets/{setIndex}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a set", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<Void>> deleteSet(@PathVariable String workoutId, @PathVariable int exerciseIndex, @PathVariable int setIndex) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.deleteSet(workoutId, userId, exerciseIndex, setIndex))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    // ==================== COMPLETION / CANCEL ====================

    @PutMapping("/{workoutId}/complete")
    @Operation(summary = "Complete a workout session", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<WorkoutResponse>> completeWorkout(@PathVariable String workoutId, @RequestBody(required = false) CompleteWorkoutRequest completionRequest) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    Map<String, Object> data = convertCompletionRequest(completionRequest); // Keep this helper or move to Mapper if complex
                    return workoutService.completeWorkout(workoutId, userId, data);
                })
                .map(workoutMapper::toResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PutMapping("/{workoutId}/cancel")
    @Operation(summary = "Cancel a workout session", security = @SecurityRequirement(name = "bearer-key"))
    public Mono<ResponseEntity<WorkoutResponse>> cancelWorkout(@PathVariable String workoutId) {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> workoutService.cancelWorkout(workoutId, userId))
                .map(workoutMapper::toResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    // ==================== DISCOVERY / ANALYTICS ====================
    // (Kept simple, delegating to services)

    @GetMapping("/available-plans")
    public Flux<WorkoutPlanSummaryResponse> getAvailablePlans(@RequestParam(required = false) String workoutType, @RequestParam(required = false) String difficulty) {
        return authenticationService.getCurrentUserId()
                .flatMapMany(workoutPlanService::findPersonalWorkoutPlans)
                .map(workoutMapper::toPlanSummary)
                .filter(summary -> matchesFilters(summary, workoutType, difficulty)); // Keep simple filter logic or move to service
    }

    // ... (Keep debug endpoints as is) ...

    // Helper: Map<String, Object> creation is simple enough to keep here or move to a RequestUtil
    private Map<String, Object> convertCompletionRequest(CompleteWorkoutRequest request) {
        if (request == null) return new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        if (request.getRating() != null) data.put("rating", request.getRating());
        if (request.getNotes() != null) data.put("notes", request.getNotes());
        if (request.getCaloriesBurned() != null) data.put("caloriesBurned", request.getCaloriesBurned());
        if (request.getAdditionalTags() != null) data.put("additionalTags", request.getAdditionalTags());
        return data;
    }

    // Simple filter matching
    private boolean matchesFilters(WorkoutPlanSummaryResponse summary, String workoutType, String difficulty) {
        if (workoutType != null && !workoutType.equalsIgnoreCase(summary.getWorkoutType())) return false;
        if (difficulty != null && !difficulty.equalsIgnoreCase(summary.getDifficulty())) return false;
        return true;
    }
}