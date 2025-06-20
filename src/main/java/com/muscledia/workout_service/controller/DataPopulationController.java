package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.service.ExerciseDataService;
import com.muscledia.workout_service.service.MuscleGroupDataService;
import com.muscledia.workout_service.service.RoutineFolderService;
import com.muscledia.workout_service.external.hevy.dto.HevyApiResponse;
import com.muscledia.workout_service.service.HevyDataService;
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
public class DataPopulationController {

    private final ExerciseDataService exerciseDataService;
    private final MuscleGroupDataService muscleGroupDataService;
    private final RoutineFolderService routineFolderService;
    private final HevyDataService hevyDataService;

    @PostMapping("/populate-exercises")
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