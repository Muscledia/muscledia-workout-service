package com.muscledia.workout_service.service;

import com.muscledia.workout_service.model.RoutineFolder;
import com.muscledia.workout_service.repository.RoutineFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineFolderService {
    private final RoutineFolderRepository routineFolderRepository;

    public Mono<RoutineFolder> findById(String id) {
        return routineFolderRepository.findById(id)
                .doOnNext(folder -> log.debug("Found routine folder: {}", folder.getTitle()))
                .switchIfEmpty(Mono.error(new RuntimeException("Routine folder not found with id: " + id)));
    }

    public Mono<RoutineFolder> findByHevyId(Long hevyId) {
        return routineFolderRepository.findByHevyId(hevyId)
                .doOnNext(folder -> log.debug("Found routine folder by Hevy ID: {}", folder.getTitle()));
    }

    public Flux<RoutineFolder> findByDifficultyLevel(String difficultyLevel) {
        return routineFolderRepository.findByDifficultyLevel(difficultyLevel)
                .doOnComplete(() -> log.debug("Retrieved routine folders for difficulty level: {}", difficultyLevel));
    }

    public Flux<RoutineFolder> findByEquipmentType(String equipmentType) {
        return routineFolderRepository.findByEquipmentType(equipmentType)
                .doOnComplete(() -> log.debug("Retrieved routine folders for equipment type: {}", equipmentType));
    }

    public Flux<RoutineFolder> findByWorkoutSplit(String workoutSplit) {
        return routineFolderRepository.findByWorkoutSplit(workoutSplit)
                .doOnComplete(() -> log.debug("Retrieved routine folders for workout split: {}", workoutSplit));
    }

    public Flux<RoutineFolder> findByDifficultyAndEquipment(String difficultyLevel, String equipmentType) {
        return routineFolderRepository.findByDifficultyLevelAndEquipmentType(difficultyLevel, equipmentType)
                .doOnComplete(() -> log.debug("Retrieved routine folders for difficulty {} and equipment: {}",
                        difficultyLevel, equipmentType));
    }

    public Flux<RoutineFolder> searchByTitle(String searchTerm) {
        return routineFolderRepository.findByTitleContainingIgnoreCase(searchTerm)
                .doOnComplete(() -> log.debug("Retrieved routine folders matching title: {}", searchTerm));
    }

    public Mono<Boolean> existsByHevyId(Long hevyId) {
        return routineFolderRepository.existsByHevyId(hevyId)
                .doOnSuccess(exists -> log.debug("Checked existence of Hevy ID: {}, exists: {}", hevyId, exists));
    }

    public Mono<RoutineFolder> save(RoutineFolder folder) {
        if (folder.getCreatedAt() == null) {
            folder.setCreatedAt(LocalDateTime.now());
        }
        folder.setUpdatedAt(LocalDateTime.now());

        return routineFolderRepository.save(folder)
                .doOnSuccess(saved -> log.debug("Saved routine folder: {}", saved.getTitle()));
    }

    public Mono<Void> deleteById(String id) {
        return routineFolderRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted routine folder with id: {}", id));
    }

    public Flux<RoutineFolder> findAll() {
        return routineFolderRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all routine folders"));
    }

    public Mono<RoutineFolder> addWorkoutPlanToFolder(String folderId, String workoutPlanId) {
        return findById(folderId)
                .flatMap(folder -> {
                    folder.getWorkoutPlanIds().add(workoutPlanId);
                    return save(folder);
                })
                .doOnSuccess(
                        folder -> log.debug("Added workout plan {} to folder {}", workoutPlanId, folder.getTitle()));
    }

    public Mono<RoutineFolder> removeWorkoutPlanFromFolder(String folderId, String workoutPlanId) {
        return findById(folderId)
                .flatMap(folder -> {
                    folder.getWorkoutPlanIds().remove(workoutPlanId);
                    return save(folder);
                })
                .doOnSuccess(folder -> log.debug("Removed workout plan {} from folder {}", workoutPlanId,
                        folder.getTitle()));
    }
}