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

    public Mono<RoutineFolder> save(RoutineFolder routineFolder) {
        if (routineFolder.getCreatedAt() == null) {
            routineFolder.setCreatedAt(LocalDateTime.now());
        }
        routineFolder.setUpdatedAt(LocalDateTime.now());
        routineFolder.parseMetadataFromTitle();

        return routineFolderRepository.save(routineFolder)
                .doOnSuccess(saved -> log.debug("Saved routine folder: {}", saved.getTitle()));
    }

    public Mono<RoutineFolder> saveToPersonalCollection(String publicFolderId, Long userId) {
        return findById(publicFolderId)
                .flatMap(publicFolder -> {
                    // Create a copy for the user's personal collection
                    RoutineFolder personalFolder = new RoutineFolder();
                    personalFolder.setTitle(publicFolder.getTitle());
                    personalFolder.setWorkoutPlanIds(publicFolder.getWorkoutPlanIds());
                    personalFolder.setDifficultyLevel(publicFolder.getDifficultyLevel());
                    personalFolder.setEquipmentType(publicFolder.getEquipmentType());
                    personalFolder.setWorkoutSplit(publicFolder.getWorkoutSplit());
                    personalFolder.setIsPublic(false); // Personal copy
                    personalFolder.setCreatedBy(userId);
                    personalFolder.setUsageCount(0L);
                    personalFolder.setCreatedAt(LocalDateTime.now());

                    // Increment usage count on original public folder
                    publicFolder.setUsageCount(publicFolder.getUsageCount() + 1);

                    return routineFolderRepository.save(publicFolder)
                            .then(routineFolderRepository.save(personalFolder));
                })
                .doOnSuccess(saved -> log.debug("Saved routine folder to personal collection for user: {}", userId));
    }

    public Flux<RoutineFolder> findPublicRoutineFolders() {
        return routineFolderRepository.findByIsPublicTrue()
                .doOnComplete(() -> log.debug("Retrieved public routine folders"));
    }

    public Flux<RoutineFolder> findPersonalRoutineFolders(Long userId) {
        return routineFolderRepository.findByCreatedByAndIsPublicFalse(userId)
                .doOnComplete(() -> log.debug("Retrieved personal routine folders for user: {}", userId));
    }

    public Mono<Void> deleteById(String id) {
        return routineFolderRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted routine folder with id: {}", id));
    }

    public Flux<RoutineFolder> findAll() {
        return routineFolderRepository.findAll()
                .map(folder -> {
                    // Parse metadata from title if not already set
                    if (folder.getDifficultyLevel() == null || folder.getEquipmentType() == null
                            || folder.getWorkoutSplit() == null) {
                        folder.parseMetadataFromTitle();
                    }
                    return folder;
                })
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