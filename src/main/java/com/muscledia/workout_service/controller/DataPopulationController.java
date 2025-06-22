package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.service.ExerciseDataService;
import com.muscledia.workout_service.service.MuscleGroupDataService;
import com.muscledia.workout_service.service.RoutineFolderService;
import com.muscledia.workout_service.external.hevy.dto.HevyApiResponse;
import com.muscledia.workout_service.service.HevyDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Population", description = "Administrative endpoints for populating reference data from external APIs (Admin Only)")
public class DataPopulationController {

    private final ExerciseDataService exerciseDataService;
    private final MuscleGroupDataService muscleGroupDataService;
    private final RoutineFolderService routineFolderService;
    private final HevyDataService hevyDataService;

    @PostMapping("/populate-exercises")
    @Operation(summary = "Populate exercises from ExerciseDB API", description = "Fetch and populate exercise data from the ExerciseDB external API. This will import all available exercises with their details including name, description, equipment, difficulty, target muscles, and animation URLs.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercise data population completed successfully", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Exercise data population initiated and completed successfully."))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error during exercise data population", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error during exercise data population: Connection timeout")))
    })
    public Mono<ResponseEntity<String>> populateExercises() {
        return exerciseDataService.fetchAndPopulateExercises()
                // .then() emits its Mono when the upstream Mono<Void> completes successfully.
                // .thenReturn() is a simpler way to achieve this for a success case.
                .thenReturn(ResponseEntity.ok("Exercise data population initiated and completed successfully."))
                .onErrorResume(error -> {
                    // Log the error for internal debugging
                    System.err.println("Error during exercise data population: " + error.getMessage());
                    // Return an Internal Server Error response to the client
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error during exercise data population: " + error.getMessage()));
                });
    }

    @PostMapping("/populate-muscle-groups")
    @Operation(summary = "Populate muscle groups from ExerciseDB API", description = "Fetch and populate muscle group data from the ExerciseDB external API. This will import all available muscle groups with their names and create normalized entries.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Muscle group data population completed successfully", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Muscle group data population completed successfully."))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error during muscle group data population", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error during muscle group data population: API unavailable")))
    })
    public Mono<ResponseEntity<String>> populateMuscleGroups() {
        return muscleGroupDataService.fetchAndPopulateMuscleGroups()
                .thenReturn(ResponseEntity.ok("Muscle group data population completed successfully."))
                .onErrorResume(error -> {
                    System.err.println("Error during muscle group data population: " + error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error during muscle group data population: " + error.getMessage()));
                });
    }

    @PostMapping("/populate-all")
    @Operation(summary = "Populate all reference data", description = "Fetch and populate both muscle groups and exercises from the ExerciseDB API in the correct order. This ensures muscle groups are available before exercises are imported.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All data population completed successfully", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "All data population completed successfully."))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error during complete data population", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error during complete data population: Database connection failed")))
    })
    public Mono<ResponseEntity<String>> populateAll() {
        return muscleGroupDataService.fetchAndPopulateMuscleGroups()
                .then(exerciseDataService.fetchAndPopulateExercises())
                .thenReturn(ResponseEntity.ok("All data population completed successfully."))
                .onErrorResume(error -> {
                    System.err.println("Error during complete data population: " + error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error during complete data population: " + error.getMessage()));
                });
    }

    @PostMapping("/cleanup-duplicate-muscle-groups")
    @Operation(summary = "Clean up duplicate muscle groups", description = "Remove duplicate muscle group entries from the database. This process identifies and removes muscle groups with similar names, keeping only normalized versions.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Duplicate muscle groups cleanup completed successfully", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Duplicate muscle groups cleanup completed successfully."))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error during muscle group cleanup", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error during muscle group cleanup: Database transaction failed")))
    })
    public Mono<ResponseEntity<String>> cleanupDuplicateMuscleGroups() {
        return muscleGroupDataService.cleanupDuplicateMuscleGroups()
                .thenReturn(ResponseEntity.ok("Duplicate muscle groups cleanup completed successfully."))
                .onErrorResume(error -> {
                    System.err.println("Error during muscle group cleanup: " + error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error during muscle group cleanup: " + error.getMessage()));
                });
    }

    @PostMapping("/populate-target-muscles-from-api")
    @Operation(summary = "Populate target muscles from API", description = "Fetch and populate target muscle data specifically from the ExerciseDB API. This supplements the muscle group data with additional target muscle information.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Target muscles from API populated successfully", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Target muscles from API populated successfully."))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error during target muscles population", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error during target muscles population: API rate limit exceeded")))
    })
    public Mono<ResponseEntity<String>> populateTargetMusclesFromApi() {
        return muscleGroupDataService.fetchTargetMusclesFromApi()
                .thenReturn(ResponseEntity.ok("Target muscles from API populated successfully."))
                .onErrorResume(error -> {
                    System.err.println("Error during target muscles population: " + error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error during target muscles population: " + error.getMessage()));
                });
    }

    @PostMapping("/hevy/populate")
    @Operation(summary = "Populate workout plans from Hevy API response", description = "Process and populate workout plans from a Hevy API response payload. This endpoint accepts the raw API response and converts it into workout plans in the system.", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully populated workout plans from Hevy", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Successfully populated workout plans from Hevy"))),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin access required"),
            @ApiResponse(responseCode = "500", description = "Error populating workout plans from Hevy", content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Error populating workout plans: Invalid data format")))
    })
    public Mono<ResponseEntity<String>> populateHevyData(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Hevy API response containing workout routines", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HevyApiResponse.class), examples = @ExampleObject(value = """
                    {
                      "routines": [
                        {
                          "id": 12345,
                          "title": "Push/Pull/Legs Routine",
                          "description": "A comprehensive 3-day split routine",
                          "exercises": []
                        }
                      ]
                    }
                    """))) @RequestBody HevyApiResponse hevyApiResponse) {
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
        log.info("Starting to fetch all data from Hevy API");
        return hevyDataService.fetchAndPopulateAllData()
                .thenReturn(ResponseEntity.ok("Successfully fetched and populated all Hevy data"))
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
                            .body("Error fetching Hevy routine folders: " + e.getMessage()));
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
            // Create test routine folders
            java.util.List<com.muscledia.workout_service.model.RoutineFolder> testFolders = java.util.Arrays.asList(
                    createTestFolder("Beginner Full Body Workout", "BEGINNER", "EQUIPMENT_FREE", "FULL_BODY"),
                    createTestFolder("Intermediate Push Pull Legs", "INTERMEDIATE", "GYM_EQUIPMENT", "PUSH_PULL_LEGS"),
                    createTestFolder("Advanced Upper Lower Split", "ADVANCED", "DUMBBELLS", "UPPER_LOWER"),
                    createTestFolder("Home Workout Collection", "BEGINNER", "EQUIPMENT_FREE", "FULL_BODY"),
                    createTestFolder("Gym Beast Mode", "ADVANCED", "GYM_EQUIPMENT", "PUSH_PULL_LEGS"));
            return testFolders;
        })
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .flatMap(folder -> {
                    // Use the routine folder service to save
                    return routineFolderService.save(folder);
                })
                .count()
                .map(count -> ResponseEntity.ok("Successfully created " + count + " test routine folders"))
                .onErrorResume(e -> {
                    log.error("Error creating test routine folders", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body("Error creating test routine folders: " + e.getMessage()));
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
}