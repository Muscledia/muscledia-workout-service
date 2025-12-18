package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.exception.UnauthorizedException;
import com.muscledia.workout_service.external.exercisedb.ExerciseDataMigrationService;
import com.muscledia.workout_service.repository.ExerciseRepository;
import com.muscledia.workout_service.service.*;
import com.muscledia.workout_service.external.hevy.dto.HevyApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Population", description = "Administrative endpoints for populating reference data from external APIs (Admin Only)")
public class DataPopulationController {

    private final AuthenticationService authenticationService;
    private final RoutineFolderService routineFolderService;
    private final HevyDataService hevyDataService;
    private final ExerciseDataMigrationService migrationService;
    private final ExerciseRepository exerciseRepository;


    // ==========================================
    // HEVY API ENDPOINTS (CLEAN - NO BUSINESS LOGIC)
    // ==========================================

    @PostMapping("/hevy/populate")
    @Operation(summary = "Populate workout plans from Hevy API response", description = "Process and populate workout plans from a Hevy API response payload. This endpoint accepts the raw API response and converts it into workout plans in the system.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully populated workout plans from Hevy", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully populated workout plans from Hevy"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error populating workout plans from Hevy", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error populating workout plans: Invalid data format")))
    })
    public Mono<ResponseEntity<String>> populateHevyData(@RequestBody HevyApiResponse hevyApiResponse) {
        log.info("Received request to populate {} workout plans from Hevy", hevyApiResponse.getRoutines().size());

        return hevyDataService.populateWorkoutPlans(hevyApiResponse)
                .thenReturn(ResponseEntity.ok("Successfully populated workout plans from Hevy"))
                .onErrorResume(e -> {
                    log.error("Error populating workout plans from Hevy", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error populating workout plans: " + e.getMessage()));
                });
    }

    @PostMapping("/hevy/fetch-all")
    @Operation(summary = "Fetch all data from Hevy API", description = "Fetch and populate all available data from the Hevy API including routines, routine folders, and workout plans. This is a comprehensive import operation.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully fetched and populated all Hevy data", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully fetched and populated all Hevy data"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error fetching data from Hevy API", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error fetching Hevy data: Authentication failed")))
    })
    public Mono<ResponseEntity<String>> fetchAllHevyData() {
        log.info("Starting comprehensive Hevy data fetch with integrity validation");

        return hevyDataService.fetchAndPopulateAllDataWithSync()
                .map(result -> {
                    String message = String.format(
                            "Successfully fetched and populated all Hevy data - Folders: %d, Plans: %d, Synced: %d, Integrity: %.1f%%",
                            (Long) result.get("totalFolders"),
                            (Long) result.get("totalWorkoutPlans"),
                            (Long) result.get("orphanedPlans"),
                            (Double) result.get("dataIntegrityScore")
                    );
                    return ResponseEntity.ok(message);
                })
                .onErrorResume(e -> {
                    log.error("Error fetching data from Hevy API", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error fetching Hevy data: " + e.getMessage()));
                });
    }

    @PostMapping("/hevy/fetch-routines")
    @Operation(summary = "Fetch routines from Hevy API", description = "Fetch and populate workout routines specifically from the Hevy API. This imports individual workout routines without folders.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully fetched and populated Hevy routines", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully fetched and populated Hevy routines"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error fetching routines from Hevy API", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error fetching Hevy routines: Network timeout")))
    })
    public Mono<ResponseEntity<String>> fetchHevyRoutines() {
        log.info("Starting to fetch routines from Hevy API");
        return hevyDataService.fetchAndPopulateRoutines()
                .thenReturn(ResponseEntity.ok("Successfully fetched and populated Hevy routines"))
                .onErrorResume(e -> {
                    log.error("Error fetching routines from Hevy API", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error fetching Hevy routines: " + e.getMessage()));
                });
    }

    @PostMapping("/hevy/fetch-folders")
    @Operation(summary = "Fetch routine folders from Hevy API", description = "Fetch and populate routine folders from the Hevy API. These are organized collections of workout routines grouped by theme or difficulty.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully fetched Hevy routine folders", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully fetched Hevy routine folders"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error fetching routine folders from Hevy API", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error fetching Hevy routine folders: Invalid API key")))
    })
    public Mono<ResponseEntity<String>> fetchHevyFolders() {
        log.info("Starting to fetch routine folders from Hevy API");
        return hevyDataService.fetchAndPopulateRoutineFolders()
                .thenReturn(ResponseEntity.ok("Successfully fetched Hevy routine folders"))
                .onErrorResume(e -> {
                    log.error("Error fetching routine folders from Hevy API", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error fetching Hevy routine folders: "
                                    + e.getMessage()));
                });
    }

    // ==========================================
    // UTILITY ENDPOINTS (CLEAN - DELEGATE TO SERVICE)
    // ==========================================

    @PostMapping("/hevy/sync-folder-workout-plan-ids")
    @Operation(
            summary = "Sync workout_plan_ids for existing folders",
            description = "Populate the workout_plan_ids field for folders that don't have it populated yet. This ensures proper relationship between folders and their workout plans.",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully synced workout_plan_ids", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully synced workout_plan_ids for 15 folders"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error during sync", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error syncing workout_plan_ids: Database connection failed")))
    })
    public Mono<ResponseEntity<String>> syncFolderWorkoutPlanIds() {
        log.info("Sync workout_plan_ids requested");

        return hevyDataService.syncFolderWorkoutPlanIds()
                .map(syncedCount -> ResponseEntity.ok("Successfully synced workout_plan_ids for " + syncedCount + " folders"))
                .onErrorResume(e -> {
                    log.error("Error syncing workout_plan_ids", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error syncing workout_plan_ids: " + e.getMessage()));
                });
    }

    @GetMapping("/hevy/folder-sync-status")
    @Operation(
            summary = "Check folder sync status",
            description = "Check how many folders need their workout_plan_ids synced and get overall statistics.",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<ResponseEntity<Map<String, Object>>> checkFolderSyncStatus() {
        log.info("Folder sync status requested");

        return hevyDataService.forceSyncAllFolderAssociations()
                .map(result -> {
                    log.info("Force sync completed: {}", result);
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    log.error("Force sync failed", e);
                    Map<String, Object> error = Map.of(
                            "error", "Sync failed: " + e.getMessage(),
                            "success", false
                    );
                    return Mono.just(ResponseEntity.internalServerError().body(error));
                });
    }

    @GetMapping("/hevy/statistics")
    @Operation(
            summary = "Get Hevy data statistics",
            description = "Get comprehensive statistics about imported Hevy data including counts and data integrity metrics.",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<ResponseEntity<Map<String, Object>>> getHevyDataStatistics() {
        log.info("Hevy data statistics requested");

        return hevyDataService.getDataStatistics()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving statistics", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @PostMapping("/create-test-routine-folders")
    @Operation(summary = "Create test routine folders", description = "Create a set of test routine folders for development and testing purposes. This populates the database with sample routine folder data.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully created test routine folders", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully created 5 test routine folders"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error creating test routine folders", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error creating test routine folders: Database constraint violation")))
    })
    public Mono<ResponseEntity<String>> createTestRoutineFolders() {
        log.info("Creating test routine folders");

        return Mono.fromCallable(() -> {
                    return java.util.Arrays
                            .asList(
                                    createTestFolder("Beginner Full Body Workout", "BEGINNER",
                                            "EQUIPMENT_FREE", "FULL_BODY"),
                                    createTestFolder("Intermediate Push Pull Legs", "INTERMEDIATE",
                                            "GYM_EQUIPMENT", "PUSH_PULL_LEGS"),
                                    createTestFolder("Advanced Upper Lower Split", "ADVANCED",
                                            "DUMBBELLS", "UPPER_LOWER"),
                                    createTestFolder("Home Workout Collection", "BEGINNER",
                                            "EQUIPMENT_FREE", "FULL_BODY"),
                                    createTestFolder("Gym Beast Mode", "ADVANCED", "GYM_EQUIPMENT",
                                            "PUSH_PULL_LEGS"));
                })
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .flatMap(routineFolderService::save)
                .count()
                .map(count -> ResponseEntity
                        .ok("Successfully created " + count + " test routine folders"))
                .onErrorResume(e -> {
                    log.error("Error creating test routine folders", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error creating test routine folders: "
                                    + e.getMessage()));
                });
    }

    private com.muscledia.workout_service.model.RoutineFolder createTestFolder(String title, String difficulty,
                                                                               String equipment, String split) {
        com.muscledia.workout_service.model.RoutineFolder folder = new com.muscledia.workout_service.model.RoutineFolder();
        folder.setTitle(title);
        folder.setDifficultyLevel(difficulty);
        folder.setEquipmentType(equipment);
        folder.setWorkoutSplit(split);
        folder.setIsPublic(true);
        folder.setCreatedBy(1L);
        folder.setUsageCount(0L);
        folder.setCreatedAt(java.time.LocalDateTime.now());
        folder.setUpdatedAt(java.time.LocalDateTime.now());
        return folder;
    }

    // ==========================================
    // EXERCISE API ENDPOINTS
    // ==========================================

    @PostMapping("/exercises/import")
    @Operation(
            summary = "Import exercises from RapidAPI",
            description = "One-time import of all exercises from RapidAPI to MongoDB. Admin access required.",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Import started successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required")
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Map<String, String>> importExercises() {
        log.info("Exercise import requested");

        return authenticationService.getCurrentUserId()
                .doOnNext(userId -> log.info("Import requested by user: {}", userId))
                .flatMap(userId -> authenticationService.isAdmin())
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        log.warn("Non-admin user attempted to import exercises");
                        return Mono.error(new UnauthorizedException("Admin access required"));
                    }

                    log.info("Starting exercise migration asynchronously");

                    CompletableFuture.runAsync(() -> {
                        try {
                            migrationService.migrateAllExercises();
                        } catch (Exception e) {
                            log.error("Error during exercise migration", e);
                        }
                    });

                    return Mono.just(Map.of(
                            "status", "started",
                            "message", "Exercise migration started. Check logs for progress.",
                            "timestamp", java.time.Instant.now().toString()
                    ));
                });
    }

    @GetMapping("/exercises/count")
    @Operation(
            summary = "Get current exercise count in database",
            description = "Returns the total number of exercises currently stored in the database",
            security = @SecurityRequirement(name = "bearer-key")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercise count retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "500", description = "Error retrieving count")
    })
    public Mono<Map<String, Object>> getExerciseCount() {
        return authenticationService.getCurrentUserId()
                .doOnNext(userId -> log.debug("Exercise count requested by user: {}", userId))
                .flatMap(userId -> exerciseRepository.count()
                        .map(count -> Map.<String, Object>of(
                                "count", count,
                                "timestamp", java.time.Instant.now().toString()
                        ))
                )
                .onErrorResume(e -> {
                    log.error("Error getting exercise count", e);
                    return Mono.just(Map.<String, Object>of(
                            "count", 0L,
                            "error", e.getMessage()
                    ));
                });
    }

    @GetMapping("/exercises/migration-status")
    @Operation(
            summary = "Get exercise migration status",
            description = "Check the status of the exercise data migration",
            security = @SecurityRequirement(name = "bearer-key")
    )
    public Mono<Map<String, Object>> getMigrationStatus() {
        return authenticationService.getCurrentUserId()
                .flatMap(userId -> exerciseRepository.count()
                        .map(count -> {
                            Map<String, Object> status = new java.util.HashMap<>();
                            status.put("totalExercises", count);
                            status.put("migrationComplete", count > 0);
                            status.put("timestamp", java.time.Instant.now().toString());

                            if (count == 0) {
                                status.put("message", "No exercises in database. Run /exercises/import to populate.");
                            } else {
                                status.put("message", count + " exercises available in database");
                            }

                            return status;
                        })
                );
    }

}