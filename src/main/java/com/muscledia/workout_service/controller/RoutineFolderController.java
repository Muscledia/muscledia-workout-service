package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.exception.SomeDuplicateEntryException;
import com.muscledia.workout_service.model.RoutineFolder;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.RoutineFolderService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/routine-folders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Routine Folders", description = "Organized collections of workout routines")
public class RoutineFolderController {
        private final RoutineFolderService routineFolderService;
        private final AuthenticationService authenticationService;

        // PUBLIC EXPLORATION ENDPOINTS (No authentication required)

        @GetMapping("/public/{id}")
        @Operation(summary = "Get public routine folder by ID", description = "Retrieve a public routine folder by its unique identifier")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Public routine folder found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "404", description = "Public routine folder not found")
        })
        public Mono<ResponseEntity<Map<String, Object>>> getPublicRoutineFolder(
                @Parameter(description = "Routine folder ID", example = "507f1f77bcf86cd799439011")
                @PathVariable String id) {

                return routineFolderService.findByIdWithWorkoutPlans(id)
                        .filter(response -> Boolean.TRUE.equals(response.get("isPublic")))
                        .map(ResponseEntity::ok)
                        .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                        .doOnSuccess(response -> log.debug("Retrieved public routine folder: {}", id));
        }

        @GetMapping("/public")
        @Operation(summary = "Get all public routine folders", description = "Retrieve all publicly available routine folders")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Public routine folders retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPublicRoutineFolders() {
                return routineFolderService.findPublicRoutineFolders();
        }

        @GetMapping("/public/hevy/{hevyId}")
        @Operation(summary = "Get public routine folder by Hevy ID", description = "Retrieve a public routine folder using its Hevy API identifier")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Public routine folder found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "404", description = "Public routine folder not found")
        })
        public Mono<ResponseEntity<RoutineFolder>> getPublicRoutineFolderByHevyId(
                        @Parameter(description = "Hevy API ID", example = "12345") @PathVariable Long hevyId) {
                return routineFolderService.findByHevyId(hevyId)
                                .filter(RoutineFolder::getIsPublic) // Only return if public
                                .map(ResponseEntity::ok)
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Retrieved public routine folder with Hevy id: {}",
                                                hevyId));
        }

        @GetMapping("/public/difficulty/{level}")
        @Operation(summary = "Get public routine folders by difficulty level", description = "Retrieve public routine folders filtered by difficulty level")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folders for difficulty level retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPublicRoutineFoldersByDifficulty(
                        @Parameter(description = "Difficulty level", example = "Intermediate") @PathVariable String level) {
                return routineFolderService.findByDifficultyLevel(level)
                                .filter(RoutineFolder::getIsPublic); // Only return public folders
        }

        @GetMapping("/public/equipment/{type}")
        @Operation(summary = "Get public routine folders by equipment type", description = "Retrieve public routine folders that require specific equipment")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folders for equipment type retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPublicRoutineFoldersByEquipment(
                        @Parameter(description = "Equipment type", example = "Gym") @PathVariable String type) {
                return routineFolderService.findByEquipmentType(type)
                                .filter(RoutineFolder::getIsPublic); // Only return public folders
        }

        @GetMapping("/public/split/{split}")
        @Operation(summary = "Get public routine folders by workout split", description = "Retrieve public routine folders that follow a specific workout split pattern")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folders for workout split retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPublicRoutineFoldersByWorkoutSplit(
                        @Parameter(description = "Workout split type", example = "Push/Pull/Legs") @PathVariable String split) {
                return routineFolderService.findByWorkoutSplit(split)
                                .filter(RoutineFolder::getIsPublic); // Only return public folders
        }

        // GET ALL ROUTINE FOLDERS (Admin/System endpoint)

        @GetMapping("/all")
        @Operation(summary = "Get all routine folders", description = "Retrieve all routine folders with metadata parsing (Admin/System endpoint)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "All routine folders retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getAllRoutineFolders() {
                return routineFolderService.findAll()
                                .map(folder -> {
                                        // Parse metadata from title if not already set
                                        if (folder.getDifficultyLevel() == null || folder.getEquipmentType() == null) {
                                                folder.parseMetadataFromTitle();
                                        }
                                        return folder;
                                })
                                .doOnSubscribe(subscription -> log.debug("Retrieving all routine folders"))
                                .doOnComplete(() -> log.debug("Completed retrieving all routine folders"));
        }

        // PERSONAL COLLECTION ENDPOINTS (Authentication required)

        @PostMapping("/save/{publicId}")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Save routine folder to personal collection", description = "Save a public routine folder to user's personal collection", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Routine folder saved to personal collection successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "404", description = "Public routine folder not found"),
                        @ApiResponse(responseCode = "409", description = "Routine folder already in personal collection")
        })
        public Mono<RoutineFolder> saveToPersonalCollection(
                @Parameter(description = "Public routine folder ID to save", example = "507f1f77bcf86cd799439011")
                @PathVariable String publicId) {

                log.info("Request to save public routine folder {} to personal collection", publicId);

                return authenticationService.getCurrentUserId()
                        .doOnNext(userId -> log.debug("Authenticated user ID: {}", userId))
                        .flatMap(userId -> routineFolderService.savePublicRoutineFolderAndPlans(publicId, userId))
                        .doOnSuccess(savedFolder -> log.info(
                                "Successfully saved routine folder '{}' to personal collection with ID: {}",
                                savedFolder.getTitle(), savedFolder.getId()))
                        .onErrorMap(this::mapToHttpException)
                        .doOnError(e -> log.error(
                                "Error saving routine folder {} to personal collection: {} - {}",
                                publicId, e.getClass().getSimpleName(), e.getMessage()));
        }

        @GetMapping("/personal")
        @Operation(summary = "Get personal routine folders", description = "Retrieve all routine folders in user's personal collection", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Personal routine folders retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class)))),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<Map<String, Object>> getPersonalRoutineFolders(
                @RequestParam(required = false, defaultValue = "true") boolean includeWorkoutPlans) {

                return authenticationService.getCurrentUserId()
                        .flatMapMany(userId -> routineFolderService.findPersonalRoutineFolders(userId)
                                .flatMap(folder ->
                                        routineFolderService.findByIdWithWorkoutPlans(folder.getId())
                                ));
        }

        @PostMapping("/personal")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create personal routine folder", description = "Create a new private routine folder for the user", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Personal routine folder created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid routine folder data"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<RoutineFolder> createPersonalRoutineFolder(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Routine folder data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class), examples = @ExampleObject(value = """
                                        {
                                          "title": "My Custom Routine Collection",
                                          "description": "A personalized collection of workout routines",
                                          "routineCount": 5,
                                          "difficultyLevel": "Intermediate",
                                          "equipmentType": "Gym",
                                          "workoutSplit": "Upper/Lower"
                                        }
                                        """))) @RequestBody RoutineFolder routineFolder) {
                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> {
                                        routineFolder.setIsPublic(false);
                                        routineFolder.setCreatedBy(userId);
                                        return routineFolderService.save(routineFolder);
                                });
        }

        // ADMIN/SYSTEM ENDPOINTS (For creating public content)

        @PostMapping("/public")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create public routine folder", description = "Create a new public routine folder (Admin only)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Public routine folder created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid routine folder data")
        })
        public Mono<RoutineFolder> createPublicRoutineFolder(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Public routine folder data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))) @RequestBody RoutineFolder routineFolder) {
                routineFolder.setIsPublic(true);
                routineFolder.setCreatedBy(1L); // System user
                return routineFolderService.save(routineFolder);
        }

        // GENERAL ENDPOINTS

        @PutMapping("/{id}")
        @Operation(summary = "Update routine folder", description = "Update an existing routine folder (only by creator)", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folder updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only update your own routine folders"),
                        @ApiResponse(responseCode = "404", description = "Routine folder not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid routine folder data")
        })
        public Mono<ResponseEntity<RoutineFolder>> updateRoutineFolder(
                        @Parameter(description = "Routine folder ID to update", example = "507f1f77bcf86cd799439011") @PathVariable String id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated routine folder data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))) @RequestBody RoutineFolder routineFolder) {
                routineFolder.setId(id);
                return routineFolderService.findById(id)
                                .flatMap(existing -> authenticationService.isCurrentUser(existing.getCreatedBy())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                return authenticationService.getCurrentUserId()
                                                                                .flatMap(userId -> {
                                                                                        routineFolder.setCreatedBy(
                                                                                                        userId);
                                                                                        return routineFolderService
                                                                                                        .save(routineFolder);
                                                                                })
                                                                                .map(ResponseEntity::ok);
                                                        } else {
                                                                return Mono.just(ResponseEntity
                                                                                .status(HttpStatus.FORBIDDEN)
                                                                                .<RoutineFolder>build());
                                                        }
                                                }))
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Updated routine folder with id: {}", id));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Delete routine folder", description = "Delete a routine folder (only by creator)", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Routine folder deleted successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Access denied - can only delete your own routine folders"),
                        @ApiResponse(responseCode = "404", description = "Routine folder not found")
        })
        public Mono<ResponseEntity<Void>> deleteRoutineFolder(
                        @Parameter(description = "Routine folder ID to delete", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return routineFolderService.findById(id)
                                .flatMap(existing -> authenticationService.isCurrentUser(existing.getCreatedBy())
                                                .flatMap(isOwner -> {
                                                        if (isOwner) {
                                                                return routineFolderService.deleteById(id)
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



        /**
         * CLEAN: Simple exception mapping without business logic
         */
        private Throwable mapToHttpException(Throwable throwable) {
                // Don't wrap existing ResponseStatusExceptions
                if (throwable instanceof ResponseStatusException) {
                        return throwable;
                }

                Throwable rootCause = getRootCause(throwable);
                String message = throwable.getMessage();

                // Map based on exception type and message patterns
                if (rootCause instanceof IllegalArgumentException) {
                        log.info("Mapping IllegalArgumentException to 404: {}", message);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, message, throwable);
                }

                if (rootCause instanceof SomeDuplicateEntryException ||
                        (message != null && message.contains("already exists"))) {
                        log.info("Mapping duplicate exception to 409: {}", message);
                        return new ResponseStatusException(HttpStatus.CONFLICT, message, throwable);
                }

                if (rootCause instanceof SecurityException) {
                        log.info("Mapping SecurityException to 403: {}", message);
                        return new ResponseStatusException(HttpStatus.FORBIDDEN, message, throwable);
                }

                // Default to 500
                log.error("Mapping unexpected exception to 500", throwable);
                return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred", throwable);
        }

        /**
         * Helper method to get root cause
         */
        private Throwable getRootCause(Throwable throwable) {
                Throwable rootCause = throwable;
                while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                        rootCause = rootCause.getCause();
                }
                return rootCause;
        }
}