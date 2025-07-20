package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.dto.request.CreateWorkoutRequest;
import com.muscledia.workout_service.dto.request.UpdateWorkoutRequest;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.WorkoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workouts", description = "Workout management endpoints (Requires Authentication)")
public class WorkoutController {

        private final WorkoutService workoutService;
        private final AuthenticationService authenticationService;

        @GetMapping
        @Operation(summary = "Get user workouts", description = "Retrieve all workouts for the authenticated user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workouts retrieved successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public Flux<Workout> getUserWorkouts() {
                return authenticationService.getCurrentUserId()
                                .doOnNext(userId -> log.info("Fetching workouts for user: {}", userId))
                                .flatMapMany(workoutService::findByUser);
        }

        @GetMapping("/{workoutId}")
        @Operation(summary = "Get workout by ID", description = "Retrieve a specific workout by ID", security = @SecurityRequirement(name = "bearer-key"))
        public Mono<Workout> getWorkoutById(
                        @Parameter(description = "Workout ID") @PathVariable String workoutId) {
                return workoutService.findById(workoutId)
                                .doOnNext(workout -> log.info("Retrieved workout: {}", workout.getId()));
        }

        @PostMapping
        @Operation(summary = "Create new workout", description = "Create a new workout for the authenticated user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Workout created successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid workout data"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<Workout> createWorkout(@Valid @RequestBody CreateWorkoutRequest request) {
                return authenticationService.getCurrentUserId()
                                .doOnNext(userId -> log.info("Creating workout for user: {}", userId))
                                .map(userId -> {
                                        // Convert request to Workout entity
                                        Workout workout = new Workout();
                                        workout.setUserId(userId);
                                        workout.setWorkoutDate(request.getWorkoutDate());
                                        workout.setDurationMinutes(request.getDurationMinutes());
                                        workout.setNotes(request.getNotes());

                                        // Convert WorkoutExerciseRequest to WorkoutExercise
                                        var exercises = request.getExercises().stream()
                                                        .map(req -> {
                                                                WorkoutExercise exercise = new WorkoutExercise();
                                                                exercise.setExerciseId(req.getExerciseId());
                                                                exercise.setSets(req.getSets());
                                                                exercise.setNotes(req.getNotes());
                                                                return exercise;
                                                        })
                                                        .collect(Collectors.toList());
                                        workout.setExercises(exercises);
                                        return workout;
                                })
                                .flatMap(workoutService::saveWorkoutAndCalculateVolume);
        }

        @DeleteMapping("/{workoutId}")
        @Operation(summary = "Delete workout", description = "Delete a workout", security = @SecurityRequirement(name = "bearer-key"))
        public Mono<Void> deleteWorkout(
                        @Parameter(description = "Workout ID") @PathVariable String workoutId) {
                return authenticationService.getCurrentUserId()
                                .doOnNext(userId -> log.info("Deleting workout {} for user: {}", workoutId, userId))
                                .then(workoutService.deleteById(workoutId));
        }

        @GetMapping("/date-range")
        @Operation(summary = "Get workouts by date range", description = "Retrieve workouts for the authenticated user within a specific date range", security = @SecurityRequirement(name = "bearer-key"))
        public Flux<Workout> getWorkoutsByDateRange(
                        @Parameter(description = "Start date") @RequestParam LocalDateTime startDate,
                        @Parameter(description = "End date") @RequestParam LocalDateTime endDate) {
                return authenticationService.getCurrentUserId()
                                .doOnNext(userId -> log.info("Fetching workouts for user {} between {} and {}", userId,
                                                startDate, endDate))
                                .flatMapMany(userId -> workoutService.findByUserAndDateRange(userId, startDate,
                                                endDate));
        }

        @GetMapping("/admin/all")
        @Operation(summary = "Get all workouts (Admin only)", description = "Administrative endpoint to retrieve all workouts from all users", security = @SecurityRequirement(name = "bearer-key"))
        @PreAuthorize("hasRole('ADMIN')")
        public Flux<Workout> getAllWorkouts() {
                return authenticationService.getCurrentUser()
                                .doOnNext(user -> log.info("Admin {} accessing all workouts", user.getUsername()))
                                .flatMapMany(user -> workoutService.findAll());
        }
}