package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.service.ExerciseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Exercises", description = "Exercise reference data and search operations")
public class ExerciseController {
        private final ExerciseService exerciseService;

        // PUBLIC EXERCISE REFERENCE DATA (No authentication required)
        // Exercises are generally public reference data for exploration

        @GetMapping("/{id}")
        @Operation(summary = "Get exercise by ID", description = "Retrieve a specific exercise by its unique identifier")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercise found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exercise.class))),
                        @ApiResponse(responseCode = "404", description = "Exercise not found")
        })
        public Mono<ResponseEntity<Exercise>> getExerciseById(
                        @Parameter(description = "Exercise ID", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return exerciseService.findById(id)
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.debug("Retrieved exercise with id: {}", id));
        }

        @GetMapping("/external/{externalId}")
        @Operation(summary = "Get exercise by external API ID", description = "Retrieve an exercise using its external API identifier (e.g., from ExerciseDB)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercise found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exercise.class))),
                        @ApiResponse(responseCode = "404", description = "Exercise not found")
        })
        public Mono<ResponseEntity<Exercise>> getExerciseByExternalId(
                        @Parameter(description = "External API ID", example = "0001") @PathVariable String externalId) {
                return exerciseService.findByExternalApiId(externalId)
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.debug("Retrieved exercise with external id: {}",
                                                externalId));
        }

        @GetMapping("/difficulty/{difficulty}")
        @Operation(summary = "Get exercises by difficulty level", description = "Retrieve exercises filtered by difficulty level with optional pagination")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByDifficulty(
                        @Parameter(description = "Exercise difficulty level", example = "INTERMEDIATE") @PathVariable ExerciseDifficulty difficulty,
                        @Parameter(description = "Page number (0-based)", example = "0") @RequestParam(required = false) Integer page,
                        @Parameter(description = "Page size", example = "20") @RequestParam(required = false) Integer size) {
                if (page != null && size != null) {
                        return exerciseService.findByDifficultyPaginated(difficulty, PageRequest.of(page, size));
                }
                return exerciseService.findByDifficulty(difficulty);
        }

        @GetMapping("/equipment/{equipment}")
        @Operation(summary = "Get exercises by equipment type", description = "Retrieve exercises that require specific equipment")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByEquipment(
                        @Parameter(description = "Equipment type", example = "barbell") @PathVariable String equipment) {
                return exerciseService.findByEquipment(equipment);
        }

        @GetMapping("/equipment/types")
        @Operation(summary = "Get exercises by multiple equipment types", description = "Retrieve exercises that can be performed with any of the specified equipment types")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByEquipmentTypes(
                        @Parameter(description = "List of equipment types", example = "[\"barbell\", \"dumbbell\"]") @RequestParam List<String> types) {
                return exerciseService.findByEquipmentTypes(types);
        }

        @GetMapping("/target-muscle/{muscle}")
        @Operation(summary = "Get exercises by target muscle", description = "Retrieve exercises that primarily target a specific muscle")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByTargetMuscle(
                        @Parameter(description = "Target muscle name", example = "biceps") @PathVariable String muscle) {
                return exerciseService.findByTargetMuscle(muscle);
        }

        @GetMapping("/target-muscles")
        @Operation(summary = "Get exercises by multiple target muscles", description = "Retrieve exercises that target any of the specified muscles")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByTargetMuscles(
                        @Parameter(description = "List of target muscles", example = "[\"biceps\", \"triceps\"]") @RequestParam List<String> muscles) {
                return exerciseService.findByTargetMuscles(muscles);
        }

        @GetMapping("/muscle-group/{muscleName}")
        @Operation(summary = "Get exercises by primary muscle group", description = "Retrieve exercises that primarily work a specific muscle group")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByPrimaryMuscleGroup(
                        @Parameter(description = "Muscle group name", example = "chest") @PathVariable String muscleName) {
                return exerciseService.findByPrimaryMuscleGroup(muscleName);
        }

        @GetMapping("/search")
        @Operation(summary = "Search exercises by name", description = "Search for exercises by name with optional difficulty filter")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> searchExercises(
                        @Parameter(description = "Exercise name to search for", example = "push") @RequestParam String name,
                        @Parameter(description = "Optional difficulty filter", example = "BEGINNER") @RequestParam(required = false) ExerciseDifficulty difficulty) {
                if (difficulty != null) {
                        return exerciseService.searchByNameAndDifficulty(name, difficulty);
                }
                return exerciseService.searchByName(name);
        }

        @GetMapping("/bodyweight")
        @Operation(summary = "Get bodyweight exercises", description = "Retrieve exercises that can be performed using only body weight")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Bodyweight exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getBodyweightExercises() {
                return exerciseService.findBodyweightExercises();
        }

        @GetMapping("/difficulty/{difficulty}/count")
        @Operation(summary = "Count exercises by difficulty", description = "Get the total count of exercises for a specific difficulty level")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Count retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Long.class)))
        })
        public Mono<Long> countExercisesByDifficulty(
                        @Parameter(description = "Exercise difficulty level", example = "INTERMEDIATE") @PathVariable ExerciseDifficulty difficulty) {
                return exerciseService.countByDifficulty(difficulty);
        }

        @GetMapping("/with-animations")
        @Operation(summary = "Get exercises with animation URLs", description = "Retrieve exercises that have animation/demonstration URLs available")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercises with animations retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesWithAnimations() {
                return exerciseService.findExercisesWithAnimations();
        }

        @GetMapping("/difficulty/{difficulty}/muscle/{muscle}")
        @Operation(summary = "Get exercises by difficulty and muscle", description = "Retrieve exercises filtered by both difficulty level and target muscle")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Filtered exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getExercisesByDifficultyAndMuscle(
                        @Parameter(description = "Exercise difficulty level", example = "INTERMEDIATE") @PathVariable ExerciseDifficulty difficulty,
                        @Parameter(description = "Target muscle name", example = "biceps") @PathVariable String muscle) {
                return exerciseService.findByDifficultyAndMuscle(difficulty, muscle);
        }

        @GetMapping
        @Operation(summary = "Get all exercises", description = "Retrieve all exercises in the database (use with caution for large datasets)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "All exercises retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Exercise.class))))
        })
        public Flux<Exercise> getAllExercises() {
                return exerciseService.findAll();
        }

        // ADMIN/SYSTEM ENDPOINTS (For managing exercise reference data)

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create new exercise", description = "Create a new exercise entry (Admin only)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Exercise created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exercise.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid exercise data"),
                        @ApiResponse(responseCode = "409", description = "Exercise already exists")
        })
        public Mono<Exercise> createExercise(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Exercise data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Exercise.class), examples = @ExampleObject(value = """
                                        {
                                          "name": "Push-up",
                                          "description": "A basic bodyweight exercise",
                                          "equipment": "body weight",
                                          "difficulty": "BEGINNER",
                                          "targetMuscle": "chest"
                                        }
                                        """))) @RequestBody Exercise exercise) {
                return exerciseService.save(exercise);
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Delete exercise", description = "Delete an exercise by ID (Admin only)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Exercise deleted successfully"),
                        @ApiResponse(responseCode = "404", description = "Exercise not found")
        })
        public Mono<Void> deleteExercise(
                        @Parameter(description = "Exercise ID to delete", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return exerciseService.deleteById(id);
        }
}