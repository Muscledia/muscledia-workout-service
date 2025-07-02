package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.dto.request.CreateWorkoutRequest;
import com.muscledia.workout_service.dto.request.UpdateWorkoutRequest;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.WorkoutMapperService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workouts", description = "Personal workout tracking and management (Requires Authentication)")
public class WorkoutController {
        private final WorkoutService workoutService;
        private final AuthenticationService authenticationService;
        private final WorkoutMapperService workoutMapperService;

        @GetMapping("/{id}")
        @Operation(summary = "Get workout by ID", description = "Retrieve a specific workout by its unique identifier. User can only access their own workouts.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workout found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Workout.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - not your workout"),
                        @ApiResponse(responseCode = "404", description = "Workout not found")
        })
        public Mono<ResponseEntity<Workout>> getWorkoutById(
                        @Parameter(description = "Workout ID", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return workoutService.findById(id)
                                .flatMap(workout -> authenticationService.isCurrentUser(workout.getUserId())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                return Mono.just(ResponseEntity.ok(workout));
                                                        } else {
                                                                return Mono.just(ResponseEntity
                                                                                .status(HttpStatus.FORBIDDEN)
                                                                                .<Workout>build());
                                                        }
                                                }))
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Retrieved workout with id: {}", id));
        }

        @GetMapping("/user/{userId}")
        @Operation(summary = "Get workouts by user", description = "Retrieve all workouts for a specific user. Users can only access their own workouts.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "User workouts retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Workout.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only access your own workouts")
        })
        public Flux<Workout> getWorkoutsByUser(
                        @Parameter(description = "User ID (must match authenticated user)", example = "12345") @PathVariable Long userId) {
                return authenticationService.isCurrentUser(userId)
                                .flatMapMany(isOwner -> {
                                        if (isOwner) {
                                                return workoutService.findByUser(userId);
                                        } else {
                                                return Flux.error(new RuntimeException(
                                                                "Access denied - can only access your own workouts"));
                                        }
                                });
        }

        @GetMapping("/user/{userId}/date-range")
        @Operation(summary = "Get workouts by user and date range", description = "Retrieve workouts for a specific user within a date range. Users can only access their own workouts.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workouts in date range retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Workout.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only access your own workouts")
        })
        public Flux<Workout> getWorkoutsByUserAndDateRange(
                        @Parameter(description = "User ID (must match authenticated user)", example = "12345") @PathVariable Long userId,
                        @Parameter(description = "Start date and time (ISO format)", example = "2024-01-01T00:00:00") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                        @Parameter(description = "End date and time (ISO format)", example = "2024-12-31T23:59:59") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
                return workoutService.findByUserAndDateRange(userId, start, end);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create new workout", description = "Create a new workout entry for the authenticated user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Workout created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Workout.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid workout data"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<Workout> createWorkout(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Workout data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateWorkoutRequest.class), examples = @ExampleObject(value = """
                                        {
                                          "workoutDate": "2024-01-15T10:30:00",
                                          "durationMinutes": 60,
                                          "totalVolume": 5250.0,
                                          "notes": "Great workout today!",
                                          "exercises": [
                                            {
                                              "exerciseId": "507f1f77bcf86cd799439011",
                                              "sets": 3,
                                              "reps": 10,
                                              "weight": 135.0,
                                              "order": 1
                                            }
                                          ]
                                        }
                                        """))) @Valid @RequestBody CreateWorkoutRequest request) {
                return authenticationService.getCurrentUserId()
                                .map(userId -> workoutMapperService.toEntity(request, userId))
                                .flatMap(workoutService::save)
                                .doOnSuccess(workout -> log.info("Created workout for user: {} with {} exercises",
                                                workout.getUserId(), workout.getExercises().size()));
        }

        @PutMapping("/{id}")
        @Operation(summary = "Update workout", description = "Update an existing workout. Users can only update their own workouts.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workout updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Workout.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only update your own workouts"),
                        @ApiResponse(responseCode = "404", description = "Workout not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid workout data")
        })
        public Mono<ResponseEntity<Workout>> updateWorkout(
                        @Parameter(description = "Workout ID to update", example = "507f1f77bcf86cd799439011") @PathVariable String id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated workout data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateWorkoutRequest.class))) @Valid @RequestBody UpdateWorkoutRequest request) {
                return workoutService.findById(id)
                                .flatMap(existingWorkout -> authenticationService
                                                .isCurrentUser(existingWorkout.getUserId())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                Workout updatedWorkout = workoutMapperService
                                                                                .updateEntity(existingWorkout, request);
                                                                return workoutService.save(updatedWorkout)
                                                                                .map(ResponseEntity::ok);
                                                        } else {
                                                                return Mono.just(ResponseEntity
                                                                                .status(HttpStatus.FORBIDDEN)
                                                                                .<Workout>build());
                                                        }
                                                }))
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Updated workout with id: {}", id));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Delete workout", description = "Delete a workout by ID. Users can only delete their own workouts.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Workout deleted successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only delete your own workouts"),
                        @ApiResponse(responseCode = "404", description = "Workout not found")
        })
        public Mono<ResponseEntity<Void>> deleteWorkout(
                        @Parameter(description = "Workout ID to delete", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return workoutService.findById(id)
                                .flatMap(workout -> authenticationService.isCurrentUser(workout.getUserId())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                return workoutService.deleteById(id)
                                                                                .then(Mono.just(ResponseEntity
                                                                                                .noContent()
                                                                                                .<Void>build()));
                                                        } else {
                                                                return Mono.just(ResponseEntity
                                                                                .status(HttpStatus.FORBIDDEN)
                                                                                .<Void>build());
                                                        }
                                                }))
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
        }

        @GetMapping("/my-workouts")
        @Operation(summary = "Get current user's workouts", description = "Retrieve all workouts for the currently authenticated user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "User workouts retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Workout.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<Workout> getCurrentUserWorkouts() {
                return authenticationService.getCurrentUserId()
                                .flatMapMany(workoutService::findByUser);
        }

        @GetMapping("/my-workouts/date-range")
        @Operation(summary = "Get current user's workouts by date range", description = "Retrieve workouts for the currently authenticated user within a date range", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workouts in date range retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Workout.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<Workout> getCurrentUserWorkoutsByDateRange(
                        @Parameter(description = "Start date and time (ISO format)", example = "2024-01-01T00:00:00") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                        @Parameter(description = "End date and time (ISO format)", example = "2024-12-31T23:59:59") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
                return authenticationService.getCurrentUserId()
                                .flatMapMany(userId -> workoutService.findByUserAndDateRange(userId, start, end));
        }

        @GetMapping
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Get all workouts", description = "Retrieve all workouts in the system (Admin only - requires admin role)", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "All workouts retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Workout.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Admin access required")
        })
        public Flux<Workout> getAllWorkouts() {
                return workoutService.findAll();
        }
}