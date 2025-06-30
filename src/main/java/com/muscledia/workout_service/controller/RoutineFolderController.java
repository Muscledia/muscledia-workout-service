package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.RoutineFolder;
import com.muscledia.workout_service.service.RoutineFolderService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/routine-folders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Routine Folders", description = "Organized collections of workout routines")
public class RoutineFolderController {
        private final RoutineFolderService routineFolderService;

        // PUBLIC EXPLORATION ENDPOINTS (No authentication required)

        @GetMapping("/public/{id}")
        @Operation(summary = "Get public routine folder by ID", description = "Retrieve a public routine folder by its unique identifier")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Public routine folder found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "404", description = "Public routine folder not found")
        })
        public Mono<ResponseEntity<RoutineFolder>> getPublicRoutineFolder(
                        @Parameter(description = "Routine folder ID", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return routineFolderService.findById(id)
                                .filter(folder -> folder.getIsPublic()) // Only return if public
                                .map(ResponseEntity::ok)
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Retrieved public routine folder with id: {}", id));
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
                                .filter(folder -> folder.getIsPublic()) // Only return if public
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
                                .filter(folder -> folder.getIsPublic()); // Only return public folders
        }

        @GetMapping("/public/equipment/{type}")
        @Operation(summary = "Get public routine folders by equipment type", description = "Retrieve public routine folders that require specific equipment")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folders for equipment type retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPublicRoutineFoldersByEquipment(
                        @Parameter(description = "Equipment type", example = "Gym") @PathVariable String type) {
                return routineFolderService.findByEquipmentType(type)
                                .filter(folder -> folder.getIsPublic()); // Only return public folders
        }

        @GetMapping("/public/split/{split}")
        @Operation(summary = "Get public routine folders by workout split", description = "Retrieve public routine folders that follow a specific workout split pattern")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folders for workout split retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPublicRoutineFoldersByWorkoutSplit(
                        @Parameter(description = "Workout split type", example = "Push/Pull/Legs") @PathVariable String split) {
                return routineFolderService.findByWorkoutSplit(split)
                                .filter(folder -> folder.getIsPublic()); // Only return public folders
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
        @Operation(summary = "Save routine folder to personal collection", description = "Save a public routine folder to user's personal collection")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Routine folder saved to personal collection successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "404", description = "Public routine folder not found"),
                        @ApiResponse(responseCode = "409", description = "Routine folder already in personal collection")
        })
        public Mono<RoutineFolder> saveToPersonalCollection(
                        @Parameter(description = "Public routine folder ID to save", example = "507f1f77bcf86cd799439011") @PathVariable String publicId,
                        @Parameter(description = "User ID (in real app, this would come from JWT token)", example = "12345") @RequestParam Long userId) { // In
                                                                                                                                                          // real
                                                                                                                                                          // app,
                                                                                                                                                          // this
                                                                                                                                                          // would
                                                                                                                                                          // come
                                                                                                                                                          // from
                                                                                                                                                          // JWT
                                                                                                                                                          // token
                return routineFolderService.saveToPersonalCollection(publicId, userId);
        }

        @GetMapping("/personal")
        @Operation(summary = "Get personal routine folders", description = "Retrieve all routine folders in user's personal collection")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Personal routine folders retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoutineFolder.class))))
        })
        public Flux<RoutineFolder> getPersonalRoutineFolders(
                        @Parameter(description = "User ID", example = "12345") @RequestParam Long userId) {
                return routineFolderService.findPersonalRoutineFolders(userId);
        }

        @PostMapping("/personal")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create personal routine folder", description = "Create a new private routine folder for the user")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Personal routine folder created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid routine folder data")
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
                                        """))) @RequestBody RoutineFolder routineFolder,
                        @Parameter(description = "User ID", example = "12345") @RequestParam Long userId) {
                routineFolder.setIsPublic(false);
                routineFolder.setCreatedBy(userId);
                return routineFolderService.save(routineFolder);
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
        @Operation(summary = "Update routine folder", description = "Update an existing routine folder (only by creator)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Routine folder updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))),
                        @ApiResponse(responseCode = "404", description = "Routine folder not found or not owned by user"),
                        @ApiResponse(responseCode = "400", description = "Invalid routine folder data")
        })
        public Mono<ResponseEntity<RoutineFolder>> updateRoutineFolder(
                        @Parameter(description = "Routine folder ID to update", example = "507f1f77bcf86cd799439011") @PathVariable String id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated routine folder data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoutineFolder.class))) @RequestBody RoutineFolder routineFolder,
                        @Parameter(description = "User ID", example = "12345") @RequestParam Long userId) {
                routineFolder.setId(id);
                // Only allow updating if user is the creator
                return routineFolderService.findById(id)
                                .filter(existing -> existing.getCreatedBy().equals(userId))
                                .flatMap(existing -> {
                                        routineFolder.setCreatedBy(userId);
                                        return routineFolderService.save(routineFolder);
                                })
                                .map(ResponseEntity::ok)
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.debug("Updated routine folder with id: {}", id));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Delete routine folder", description = "Delete a routine folder (only by creator)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Routine folder deleted successfully"),
                        @ApiResponse(responseCode = "404", description = "Routine folder not found or not owned by user")
        })
        public Mono<Void> deleteRoutineFolder(
                        @Parameter(description = "Routine folder ID to delete", example = "507f1f77bcf86cd799439011") @PathVariable String id,
                        @Parameter(description = "User ID", example = "12345") @RequestParam Long userId) {
                // Only allow deleting if user is the creator
                return routineFolderService.findById(id)
                                .filter(existing -> existing.getCreatedBy().equals(userId))
                                .flatMap(existing -> routineFolderService.deleteById(id))
                                .then();
        }
}