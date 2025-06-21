package com.muscledia.workout_service.repository;

import com.muscledia.workout_service.model.RoutineFolder;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RoutineFolderRepository extends ReactiveMongoRepository<RoutineFolder, String> {
    Mono<RoutineFolder> findByHevyId(Long hevyId);

    Flux<RoutineFolder> findByDifficultyLevel(String difficultyLevel);

    Flux<RoutineFolder> findByEquipmentType(String equipmentType);

    Flux<RoutineFolder> findByWorkoutSplit(String workoutSplit);

    Flux<RoutineFolder> findByDifficultyLevelAndEquipmentType(String difficultyLevel, String equipmentType);

    Flux<RoutineFolder> findByTitleContainingIgnoreCase(String searchTerm);

    // Find public routine folders
    Flux<RoutineFolder> findByIsPublicTrue();

    // Find personal routine folders (private, created by user)
    Flux<RoutineFolder> findByCreatedByAndIsPublicFalse(Long userId);

    Mono<Boolean> existsByHevyId(Long hevyId);
}