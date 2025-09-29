package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.MuscleGroup;
import com.muscledia.workout_service.service.MuscleGroupService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/muscle-groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Muscle Groups", description = "Muscle group reference data and operations")
public class MuscleGroupController {
        private final MuscleGroupService muscleGroupService;

        // PUBLIC MUSCLE GROUP REFERENCE DATA (No authentication required)
        // Muscle groups are generally public reference data for exploration

        @GetMapping("/{id}")
        @Operation(summary = "Get muscle group by ID", description = "Retrieve a specific muscle group by its unique identifier")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Muscle group found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MuscleGroup.class))),
                        @ApiResponse(responseCode = "404", description = "Muscle group not found")
        })
        public Mono<ResponseEntity<MuscleGroup>> getMuscleGroupById(
                        @Parameter(description = "Muscle group ID", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return muscleGroupService.findById(id)
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.debug("Retrieved muscle group with id: {}", id));
        }

        @GetMapping("/name/{name}")
        @Operation(summary = "Get muscle group by name", description = "Retrieve a muscle group by its common name")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Muscle group found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MuscleGroup.class))),
                        @ApiResponse(responseCode = "404", description = "Muscle group not found")
        })
        public Mono<ResponseEntity<MuscleGroup>> getMuscleGroupByName(
                        @Parameter(description = "Muscle group name", example = "biceps") @PathVariable String name) {
                return muscleGroupService.findByName(name)
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.debug("Retrieved muscle group with name: {}", name));
        }

        @GetMapping("/latin-name/{latinName}")
        @Operation(summary = "Get muscle group by Latin name", description = "Retrieve a muscle group by its Latin anatomical name")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Muscle group found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MuscleGroup.class))),
                        @ApiResponse(responseCode = "404", description = "Muscle group not found")
        })
        public Mono<ResponseEntity<MuscleGroup>> getMuscleGroupByLatinName(
                        @Parameter(description = "Latin name of the muscle group", example = "biceps brachii") @PathVariable String latinName) {
                return muscleGroupService.findByLatinName(latinName)
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.debug("Retrieved muscle group with Latin name: {}",
                                                latinName));
        }

        @GetMapping("/search")
        @Operation(summary = "Search muscle groups", description = "Search muscle groups by name or Latin name using a search term")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> searchMuscleGroups(
                        @Parameter(description = "Search term to match against name or Latin name", example = "bic") @RequestParam String term) {
                return muscleGroupService.searchByNameOrLatinName(term);
        }

        @GetMapping("/search/name")
        @Operation(summary = "Search muscle groups by name", description = "Search muscle groups by common name using partial matching")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> searchByName(
                        @Parameter(description = "Name search term", example = "chest") @RequestParam String name) {
                return muscleGroupService.searchByName(name);
        }

        @GetMapping("/search/latin-name")
        @Operation(summary = "Search muscle groups by Latin name", description = "Search muscle groups by Latin name using partial matching")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> searchByLatinName(
                        @Parameter(description = "Latin name search term", example = "pectoralis") @RequestParam String latinName) {
                return muscleGroupService.searchByLatinName(latinName);
        }

        @GetMapping("/names")
        @Operation(summary = "Get muscle groups by names", description = "Retrieve multiple muscle groups by providing a list of names")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Muscle groups retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> getMuscleGroupsByNames(
                        @Parameter(description = "List of muscle group names", example = "[\"biceps\", \"triceps\", \"chest\"]") @RequestParam List<String> names) {
                return muscleGroupService.findByNames(names);
        }

        @GetMapping("/with-descriptions")
        @Operation(summary = "Get muscle groups with descriptions", description = "Retrieve muscle groups that have description text available")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Muscle groups with descriptions retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> getMuscleGroupsWithDescriptions() {
                return muscleGroupService.findWithDescriptions();
        }

        @GetMapping("/search/description")
        @Operation(summary = "Search muscle groups by description", description = "Search muscle groups by description content using text matching")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> searchByDescription(
                        @Parameter(description = "Description search term", example = "upper arm") @RequestParam String term) {
                return muscleGroupService.searchByDescription(term);
        }

        @GetMapping("/exists/{name}")
        @Operation(summary = "Check if muscle group exists", description = "Check whether a muscle group with the given name exists in the database")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Existence check completed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Boolean.class)))
        })
        public Mono<Boolean> checkMuscleGroupExists(
                        @Parameter(description = "Muscle group name to check", example = "biceps") @PathVariable String name) {
                return muscleGroupService.existsByName(name);
        }

        @GetMapping
        @Operation(summary = "Get all muscle groups", description = "Retrieve all muscle groups in the database")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "All muscle groups retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MuscleGroup.class))))
        })
        public Flux<MuscleGroup> getAllMuscleGroups() {
                return muscleGroupService.findAll();
        }

        // ADMIN/SYSTEM ENDPOINTS (For managing muscle group reference data)

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create new muscle group", description = "Create a new muscle group entry (Admin only)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Muscle group created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MuscleGroup.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid muscle group data"),
                        @ApiResponse(responseCode = "409", description = "Muscle group already exists")
        })
        public Mono<MuscleGroup> createMuscleGroup(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Muscle group data to create", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MuscleGroup.class), examples = @ExampleObject(value = """
                                        {
                                          "name": "biceps",
                                          "latinName": "biceps brachii",
                                          "description": "The biceps muscle is located on the front of the upper arm"
                                        }
                                        """))) @RequestBody MuscleGroup muscleGroup) {
                return muscleGroupService.save(muscleGroup);
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Delete muscle group", description = "Delete a muscle group by ID (Admin only)")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Muscle group deleted successfully"),
                        @ApiResponse(responseCode = "404", description = "Muscle group not found")
        })
        public Mono<Void> deleteMuscleGroup(
                        @Parameter(description = "Muscle group ID to delete", example = "507f1f77bcf86cd799439011") @PathVariable String id) {
                return muscleGroupService.deleteById(id);
        }
}