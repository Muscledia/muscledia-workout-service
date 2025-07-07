package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.WorkoutPlanService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/workout-plans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workout Plans", description = "Workout plan templates and routines")
public class WorkoutPlanController {
        private final WorkoutPlanService workoutPlanService;
        private final AuthenticationService authenticationService;

        // PUBLIC EXPLORATION ENDPOINTS (No authentication required)

        @GetMapping("/public/{id}")
        @Operation(summary = "Get public workout plan by ID", description = "Retrieve a public workout plan by its unique identifier")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Public workout plan found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))),
                        @ApiResponse(responseCode = "404", description = "Public workout plan not found")
        })
        public Mono<ResponseEntity<WorkoutPlan>> getPublicWorkoutPlan(
                        @Parameter(description = "Workout plan ID", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return workoutPlanService.findById(id)
                                .filter(plan -> plan.getIsPublic()) // Only return if public
                                .map(ResponseEntity::ok)
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Retrieved public workout plan with id: {}", id));
        }

        @GetMapping("/public")
        @Operation(summary = "Get all public workout plans", description = "Retrieve all publicly available workout plans")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Public workout plans retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> getPublicWorkoutPlans() {
                return workoutPlanService.findPublicWorkoutPlans();
        }

        @GetMapping("/public/search")
        @Operation(summary = "Search public workout plans", description = "Search through public workout plans using a search term")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> searchPublicWorkoutPlans(
                        @Parameter(description = "Search term to match against plan name or description", example = "strength") @RequestParam String term) {
                return workoutPlanService.searchPublicWorkouts(term);
        }

        @GetMapping("/public/popular")
        @Operation(summary = "Get popular workout plans", description = "Retrieve the most popular public workout plans with pagination")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Popular workout plans retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> getPopularWorkoutPlans(
                        @Parameter(description = "Page number (0-based)", example = "0") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10") int size) {
                return workoutPlanService.findPopularWorkoutPlans(PageRequest.of(page, size));
        }

        @GetMapping("/public/recent")
        @Operation(summary = "Get recent workout plans", description = "Retrieve the most recently created public workout plans with pagination")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Recent workout plans retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> getRecentWorkoutPlans(
                        @Parameter(description = "Page number (0-based)", example = "0") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10") int size) {
                return workoutPlanService.findRecentWorkoutPlans(PageRequest.of(page, size));
        }

        @GetMapping("/public/muscle-group/{muscleGroup}")
        @Operation(summary = "Get public workout plans by muscle group", description = "Retrieve public workout plans that target a specific muscle group")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workout plans for muscle group retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> getPublicWorkoutPlansByMuscleGroup(
                        @Parameter(description = "Target muscle group", example = "chest") @PathVariable String muscleGroup) {
                return workoutPlanService.findByTargetMuscleGroup(muscleGroup)
                                .filter(plan -> plan.getIsPublic()); // Only return public plans
        }

        @GetMapping("/public/equipment/{equipment}")
        @Operation(summary = "Get public workout plans by equipment", description = "Retrieve public workout plans that require specific equipment")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workout plans for equipment retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> getPublicWorkoutPlansByEquipment(
                        @Parameter(description = "Required equipment type", example = "barbell") @PathVariable String equipment) {
                return workoutPlanService.findByRequiredEquipment(equipment)
                                .filter(plan -> plan.getIsPublic()); // Only return public plans
        }

        @GetMapping("/public/duration")
        @Operation(summary = "Get public workout plans by duration range", description = "Retrieve public workout plans within a specific duration range (in minutes)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workout plans in duration range retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class))))
        })
        public Flux<WorkoutPlan> getPublicWorkoutPlansByDuration(
                        @Parameter(description = "Minimum duration in minutes", example = "30") @RequestParam Integer minDuration,
                        @Parameter(description = "Maximum duration in minutes", example = "60") @RequestParam Integer maxDuration) {
                return workoutPlanService.findByDurationRange(minDuration, maxDuration)
                                .filter(plan -> plan.getIsPublic()); // Only return public plans
        }

        // PERSONAL COLLECTION ENDPOINTS (Authentication required)

        @PostMapping("/save/{publicId}")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Save workout plan to personal collection", description = "Save a public workout plan to user's personal collection", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Workout plan saved to personal collection successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "404", description = "Public workout plan not found"),
                        @ApiResponse(responseCode = "409", description = "Workout plan already in personal collection")
        })
        public Mono<WorkoutPlan> saveToPersonalCollection(
                        @Parameter(description = "Public workout plan ID to save", example = "507f1f77bcf86cd799439011") @PathVariable String publicId) {
                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> workoutPlanService.saveToPersonalCollection(publicId, userId));
        }

        @GetMapping("/personal")
        @Operation(summary = "Get personal workout plans", description = "Retrieve all workout plans in user's personal collection", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Personal workout plans retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<WorkoutPlan> getPersonalWorkoutPlans() {
                return authenticationService.getCurrentUserId()
                                .flatMapMany(workoutPlanService::findPersonalWorkoutPlans);
        }

        @GetMapping("/my-created")
        @Operation(summary = "Get user-created workout plans", description = "Retrieve workout plans created by the user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "User-created workout plans retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WorkoutPlan.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<WorkoutPlan> getMyCreatedWorkoutPlans() {
                return authenticationService.getCurrentUserId()
                                .flatMapMany(workoutPlanService::findByCreator);
        }

        @PostMapping("/personal")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create personal workout plan", description = "Create a new private workout plan for the user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Personal workout plan created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid workout plan data"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<WorkoutPlan> createPersonalWorkoutPlan(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Workout plan data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class), examples = @ExampleObject(value = """
                                        {
                                          "name": "My Custom Workout",
                                          "description": "A personalized strength training routine",
                                          "estimatedDuration": 45,
                                          "difficulty": "INTERMEDIATE",
                                          "workoutType": "STRENGTH",
                                          "exercises": [
                                            {
                                              "exerciseId": "507f1f77bcf86cd799439011",
                                              "exerciseName": "Bench Press",
                                              "sets": 3,
                                              "reps": 10,
                                              "weight": 135.0,
                                              "restTime": 90
                                            }
                                          ]
                                        }
                                        """))) @RequestBody WorkoutPlan workoutPlan) {
                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> {
                                        workoutPlan.setIsPublic(false);
                                        workoutPlan.setCreatedBy(userId);
                                        return workoutPlanService.save(workoutPlan);
                                });
        }

        // ADMIN/SYSTEM ENDPOINTS (For creating public content)

        @PostMapping("/public")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create public workout plan", description = "Create a new public workout plan (Admin only)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Public workout plan created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid workout plan data")
        })
        public Mono<WorkoutPlan> createPublicWorkoutPlan(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Public workout plan data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))) @RequestBody WorkoutPlan workoutPlan) {
                workoutPlan.setIsPublic(true);
                workoutPlan.setCreatedBy(1L); // System user
                return workoutPlanService.save(workoutPlan);
        }

        // GENERAL ENDPOINTS

        @PutMapping("/{id}")
        @Operation(summary = "Update workout plan", description = "Update an existing workout plan (only by creator)", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Workout plan updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only update your own workout plans"),
                        @ApiResponse(responseCode = "404", description = "Workout plan not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid workout plan data")
        })
        public Mono<ResponseEntity<WorkoutPlan>> updateWorkoutPlan(
                        @Parameter(description = "Workout plan ID to update", example = "507f1f77bcf86cd799439011") @PathVariable String id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated workout plan data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutPlan.class))) @RequestBody WorkoutPlan workoutPlan) {
                workoutPlan.setId(id);
                return workoutPlanService.findById(id)
                                .flatMap(existing -> authenticationService.isCurrentUser(existing.getCreatedBy())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                return authenticationService.getCurrentUserId()
                                                                                .flatMap(userId -> {
                                                                                        workoutPlan.setCreatedBy(
                                                                                                        userId);
                                                                                        return workoutPlanService.save(
                                                                                                        workoutPlan);
                                                                                })
                                                                                .map(ResponseEntity::ok);
                                                        } else {
                                                                return Mono.just(ResponseEntity
                                                                                .status(HttpStatus.FORBIDDEN)
                                                                                .<WorkoutPlan>build());
                                                        }
                                                }))
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Updated workout plan with id: {}", id));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Delete workout plan", description = "Delete a workout plan (only by creator)", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Workout plan deleted successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only delete your own workout plans"),
                        @ApiResponse(responseCode = "404", description = "Workout plan not found")
        })
        public Mono<ResponseEntity<Void>> deleteWorkoutPlan(
                        @Parameter(description = "Workout plan ID to delete", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return workoutPlanService.findById(id)
                                .flatMap(existing -> authenticationService.isCurrentUser(existing.getCreatedBy())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                return workoutPlanService.deleteById(id)
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
}