package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.MuscleGroup;
import com.muscledia.workout_service.service.MuscleGroupService;
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
public class MuscleGroupController {
    private final MuscleGroupService muscleGroupService;

    // PUBLIC MUSCLE GROUP REFERENCE DATA (No authentication required)
    // Muscle groups are generally public reference data for exploration

    @GetMapping("/{id}")
    public Mono<ResponseEntity<MuscleGroup>> getMuscleGroupById(@PathVariable String id) {
        return muscleGroupService.findById(id)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Retrieved muscle group with id: {}", id));
    }

    @GetMapping("/name/{name}")
    public Mono<ResponseEntity<MuscleGroup>> getMuscleGroupByName(@PathVariable String name) {
        return muscleGroupService.findByName(name)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Retrieved muscle group with name: {}", name));
    }

    @GetMapping("/latin-name/{latinName}")
    public Mono<ResponseEntity<MuscleGroup>> getMuscleGroupByLatinName(@PathVariable String latinName) {
        return muscleGroupService.findByLatinName(latinName)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Retrieved muscle group with Latin name: {}", latinName));
    }

    @GetMapping("/search")
    public Flux<MuscleGroup> searchMuscleGroups(@RequestParam String term) {
        return muscleGroupService.searchByNameOrLatinName(term);
    }

    @GetMapping("/search/name")
    public Flux<MuscleGroup> searchByName(@RequestParam String name) {
        return muscleGroupService.searchByName(name);
    }

    @GetMapping("/search/latin-name")
    public Flux<MuscleGroup> searchByLatinName(@RequestParam String latinName) {
        return muscleGroupService.searchByLatinName(latinName);
    }

    @GetMapping("/names")
    public Flux<MuscleGroup> getMuscleGroupsByNames(@RequestParam List<String> names) {
        return muscleGroupService.findByNames(names);
    }

    @GetMapping("/with-descriptions")
    public Flux<MuscleGroup> getMuscleGroupsWithDescriptions() {
        return muscleGroupService.findWithDescriptions();
    }

    @GetMapping("/search/description")
    public Flux<MuscleGroup> searchByDescription(@RequestParam String term) {
        return muscleGroupService.searchByDescription(term);
    }

    @GetMapping("/exists/{name}")
    public Mono<Boolean> checkMuscleGroupExists(@PathVariable String name) {
        return muscleGroupService.existsByName(name);
    }

    @GetMapping
    public Flux<MuscleGroup> getAllMuscleGroups() {
        return muscleGroupService.findAll();
    }

    // ADMIN/SYSTEM ENDPOINTS (For managing muscle group reference data)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MuscleGroup> createMuscleGroup(@RequestBody MuscleGroup muscleGroup) {
        return muscleGroupService.save(muscleGroup);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteMuscleGroup(@PathVariable String id) {
        return muscleGroupService.deleteById(id);
    }
}