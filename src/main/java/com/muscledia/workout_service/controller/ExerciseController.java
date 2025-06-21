package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.service.ExerciseService;
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
public class ExerciseController {
    private final ExerciseService exerciseService;

    // PUBLIC EXERCISE REFERENCE DATA (No authentication required)
    // Exercises are generally public reference data for exploration

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Exercise>> getExerciseById(@PathVariable String id) {
        return exerciseService.findById(id)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Retrieved exercise with id: {}", id));
    }

    @GetMapping("/external/{externalId}")
    public Mono<ResponseEntity<Exercise>> getExerciseByExternalId(@PathVariable String externalId) {
        return exerciseService.findByExternalApiId(externalId)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Retrieved exercise with external id: {}", externalId));
    }

    @GetMapping("/difficulty/{difficulty}")
    public Flux<Exercise> getExercisesByDifficulty(
            @PathVariable ExerciseDifficulty difficulty,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            return exerciseService.findByDifficultyPaginated(difficulty, PageRequest.of(page, size));
        }
        return exerciseService.findByDifficulty(difficulty);
    }

    @GetMapping("/equipment/{equipment}")
    public Flux<Exercise> getExercisesByEquipment(@PathVariable String equipment) {
        return exerciseService.findByEquipment(equipment);
    }

    @GetMapping("/equipment/types")
    public Flux<Exercise> getExercisesByEquipmentTypes(@RequestParam List<String> types) {
        return exerciseService.findByEquipmentTypes(types);
    }

    @GetMapping("/target-muscle/{muscle}")
    public Flux<Exercise> getExercisesByTargetMuscle(@PathVariable String muscle) {
        return exerciseService.findByTargetMuscle(muscle);
    }

    @GetMapping("/target-muscles")
    public Flux<Exercise> getExercisesByTargetMuscles(@RequestParam List<String> muscles) {
        return exerciseService.findByTargetMuscles(muscles);
    }

    @GetMapping("/muscle-group/{muscleName}")
    public Flux<Exercise> getExercisesByPrimaryMuscleGroup(@PathVariable String muscleName) {
        return exerciseService.findByPrimaryMuscleGroup(muscleName);
    }

    @GetMapping("/search")
    public Flux<Exercise> searchExercises(
            @RequestParam String name,
            @RequestParam(required = false) ExerciseDifficulty difficulty) {
        if (difficulty != null) {
            return exerciseService.searchByNameAndDifficulty(name, difficulty);
        }
        return exerciseService.searchByName(name);
    }

    @GetMapping("/bodyweight")
    public Flux<Exercise> getBodyweightExercises() {
        return exerciseService.findBodyweightExercises();
    }

    @GetMapping("/difficulty/{difficulty}/count")
    public Mono<Long> countExercisesByDifficulty(@PathVariable ExerciseDifficulty difficulty) {
        return exerciseService.countByDifficulty(difficulty);
    }

    @GetMapping("/with-animations")
    public Flux<Exercise> getExercisesWithAnimations() {
        return exerciseService.findExercisesWithAnimations();
    }

    @GetMapping("/difficulty/{difficulty}/muscle/{muscle}")
    public Flux<Exercise> getExercisesByDifficultyAndMuscle(
            @PathVariable ExerciseDifficulty difficulty,
            @PathVariable String muscle) {
        return exerciseService.findByDifficultyAndMuscle(difficulty, muscle);
    }

    @GetMapping
    public Flux<Exercise> getAllExercises() {
        return exerciseService.findAll();
    }

    // ADMIN/SYSTEM ENDPOINTS (For managing exercise reference data)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Exercise> createExercise(@RequestBody Exercise exercise) {
        return exerciseService.save(exercise);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteExercise(@PathVariable String id) {
        return exerciseService.deleteById(id);
    }
}