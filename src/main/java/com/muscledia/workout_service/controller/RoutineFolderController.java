package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.RoutineFolder;
import com.muscledia.workout_service.service.RoutineFolderService;
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
public class RoutineFolderController {
    private final RoutineFolderService routineFolderService;

    // PUBLIC EXPLORATION ENDPOINTS (No authentication required)

    @GetMapping("/public/{id}")
    public Mono<ResponseEntity<RoutineFolder>> getPublicRoutineFolder(@PathVariable String id) {
        return routineFolderService.findById(id)
                .filter(folder -> folder.getIsPublic()) // Only return if public
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(response -> log.debug("Retrieved public routine folder with id: {}", id));
    }

    @GetMapping("/public")
    public Flux<RoutineFolder> getPublicRoutineFolders() {
        return routineFolderService.findPublicRoutineFolders();
    }

    @GetMapping("/public/hevy/{hevyId}")
    public Mono<ResponseEntity<RoutineFolder>> getPublicRoutineFolderByHevyId(@PathVariable Long hevyId) {
        return routineFolderService.findByHevyId(hevyId)
                .filter(folder -> folder.getIsPublic()) // Only return if public
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(response -> log.debug("Retrieved public routine folder with Hevy id: {}", hevyId));
    }

    @GetMapping("/public/difficulty/{level}")
    public Flux<RoutineFolder> getPublicRoutineFoldersByDifficulty(@PathVariable String level) {
        return routineFolderService.findByDifficultyLevel(level)
                .filter(folder -> folder.getIsPublic()); // Only return public folders
    }

    @GetMapping("/public/equipment/{type}")
    public Flux<RoutineFolder> getPublicRoutineFoldersByEquipment(@PathVariable String type) {
        return routineFolderService.findByEquipmentType(type)
                .filter(folder -> folder.getIsPublic()); // Only return public folders
    }

    @GetMapping("/public/split/{split}")
    public Flux<RoutineFolder> getPublicRoutineFoldersByWorkoutSplit(@PathVariable String split) {
        return routineFolderService.findByWorkoutSplit(split)
                .filter(folder -> folder.getIsPublic()); // Only return public folders
    }

    // GET ALL ROUTINE FOLDERS (Admin/System endpoint)

    @GetMapping("/all")
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
    public Mono<RoutineFolder> saveToPersonalCollection(
            @PathVariable String publicId,
            @RequestParam Long userId) { // In real app, this would come from JWT token
        return routineFolderService.saveToPersonalCollection(publicId, userId);
    }

    @GetMapping("/personal")
    public Flux<RoutineFolder> getPersonalRoutineFolders(@RequestParam Long userId) {
        return routineFolderService.findPersonalRoutineFolders(userId);
    }

    @PostMapping("/personal")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RoutineFolder> createPersonalRoutineFolder(
            @RequestBody RoutineFolder routineFolder,
            @RequestParam Long userId) {
        routineFolder.setIsPublic(false);
        routineFolder.setCreatedBy(userId);
        return routineFolderService.save(routineFolder);
    }

    // ADMIN/SYSTEM ENDPOINTS (For creating public content)

    @PostMapping("/public")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RoutineFolder> createPublicRoutineFolder(@RequestBody RoutineFolder routineFolder) {
        routineFolder.setIsPublic(true);
        routineFolder.setCreatedBy(1L); // System user
        return routineFolderService.save(routineFolder);
    }

    // GENERAL ENDPOINTS

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RoutineFolder>> updateRoutineFolder(
            @PathVariable String id,
            @RequestBody RoutineFolder routineFolder,
            @RequestParam Long userId) {
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
    public Mono<Void> deleteRoutineFolder(@PathVariable String id, @RequestParam Long userId) {
        // Only allow deleting if user is the creator
        return routineFolderService.findById(id)
                .filter(existing -> existing.getCreatedBy().equals(userId))
                .flatMap(existing -> routineFolderService.deleteById(id))
                .then();
    }
}