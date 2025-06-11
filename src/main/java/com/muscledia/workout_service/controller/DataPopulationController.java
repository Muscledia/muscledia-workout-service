package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.service.ExerciseDataService;
import com.muscledia.workout_service.service.MuscleGroupDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataPopulationController {

    private final ExerciseDataService exerciseDataService;
    private final MuscleGroupDataService muscleGroupDataService;

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
}