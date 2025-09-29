package com.muscledia.workout_service.service;

import com.muscledia.workout_service.exception.ResourceNotFoundException;
import com.muscledia.workout_service.exception.ValidationException;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExerciseService {
    private final ExerciseRepository exerciseRepository;

    public Mono<Exercise> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.error(new ValidationException("id", "Exercise ID cannot be null or empty"));
        }
        return exerciseRepository.findById(id)
                .doOnNext(exercise -> log.debug("Found exercise: {}", exercise.getName()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Exercise", "id", id)));
    }

    public Mono<Exercise> findByExternalApiId(String externalApiId) {
        if (externalApiId == null || externalApiId.trim().isEmpty()) {
            return Mono.error(new ValidationException("externalApiId", "External API ID cannot be null or empty"));
        }
        return exerciseRepository.findByExternalApiId(externalApiId)
                .doOnNext(exercise -> log.debug("Found exercise by external API ID: {}", exercise.getName()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Exercise", "externalApiId", externalApiId)));
    }

    public Flux<Exercise> findByDifficulty(ExerciseDifficulty difficulty) {
        if (difficulty == null) {
            return Flux.error(new ValidationException("difficulty", "Difficulty level cannot be null"));
        }
        return exerciseRepository.findByDifficulty(difficulty)
                .doOnComplete(() -> log.debug("Retrieved exercises for difficulty: {}", difficulty));
    }

    public Flux<Exercise> findByDifficultyPaginated(ExerciseDifficulty difficulty, Pageable pageable) {
        if (difficulty == null) {
            return Flux.error(new ValidationException("difficulty", "Difficulty level cannot be null"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByDifficulty(difficulty, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for difficulty: {}", difficulty));
    }

    public Flux<Exercise> findByEquipment(String equipment) {
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        return exerciseRepository.findByEquipment(equipment)
                .doOnComplete(() -> log.debug("Retrieved exercises for equipment: {}", equipment));
    }

    public Flux<Exercise> findByEquipmentTypes(List<String> equipmentList) {
        return exerciseRepository.findByEquipmentIn(equipmentList)
                .doOnComplete(() -> log.debug("Retrieved exercises for equipment types: {}", equipmentList));
    }

    public Flux<Exercise> findByTargetMuscle(String targetMuscle) {
        return exerciseRepository.findByTargetMuscle(targetMuscle)
                .doOnComplete(() -> log.debug("Retrieved exercises for target muscle: {}", targetMuscle));
    }

    public Flux<Exercise> findByTargetMuscles(List<String> targetMuscles) {
        return exerciseRepository.findByTargetMuscleIn(targetMuscles)
                .doOnComplete(() -> log.debug("Retrieved exercises for target muscles: {}", targetMuscles));
    }

    public Flux<Exercise> findByPrimaryMuscleGroup(String muscleName) {
        return exerciseRepository.findByPrimaryMuscleGroup(muscleName)
                .doOnComplete(() -> log.debug("Retrieved exercises for primary muscle group: {}", muscleName));
    }

    public Flux<Exercise> searchByName(String name) {
        return exerciseRepository.findByNameContainingIgnoreCase(name)
                .doOnComplete(() -> log.debug("Retrieved exercises matching name: {}", name));
    }

    public Flux<Exercise> searchByNameAndDifficulty(String name, ExerciseDifficulty difficulty) {
        return exerciseRepository.findByNameContainingIgnoreCaseAndDifficulty(name, difficulty)
                .doOnComplete(
                        () -> log.debug("Retrieved exercises matching name and difficulty: {}, {}", name, difficulty));
    }

    public Flux<Exercise> findBodyweightExercises() {
        return exerciseRepository.findBodyweightExercises()
                .doOnComplete(() -> log.debug("Retrieved bodyweight exercises"));
    }

    public Mono<Long> countByDifficulty(ExerciseDifficulty difficulty) {
        if (difficulty == null) {
            return Mono.error(new ValidationException("difficulty", "Difficulty level cannot be null"));
        }
        return exerciseRepository.countByDifficulty(difficulty)
                .doOnSuccess(count -> log.debug("Counted {} exercises for difficulty: {}", count, difficulty));
    }

    public Flux<Exercise> findExercisesWithAnimations() {
        return exerciseRepository.findByAnimationUrlIsNotNull()
                .doOnComplete(() -> log.debug("Retrieved exercises with animations"));
    }

    public Flux<Exercise> findByDifficultyAndMuscle(ExerciseDifficulty difficulty, String muscle) {
        if (difficulty == null) {
            return Flux.error(new ValidationException("difficulty", "Difficulty level cannot be null"));
        }
        if (muscle == null || muscle.trim().isEmpty()) {
            return Flux.error(new ValidationException("muscle", "Muscle cannot be null or empty"));
        }
        return exerciseRepository.findByDifficultyAndMuscleInvolved(difficulty, muscle)
                .doOnComplete(
                        () -> log.debug("Retrieved exercises for difficulty and muscle: {}, {}", difficulty, muscle));
    }

    public Mono<Exercise> save(Exercise exercise) {
        if (exercise == null) {
            return Mono.error(new ValidationException("exercise", "Exercise cannot be null"));
        }
        if (exercise.getName() == null || exercise.getName().trim().isEmpty()) {
            return Mono.error(new ValidationException("name", "Exercise name cannot be null or empty"));
        }
        return exerciseRepository.save(exercise)
                .doOnSuccess(saved -> log.debug("Saved exercise: {}", saved.getName()));
    }

    public Mono<Void> deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.error(new ValidationException("id", "Exercise ID cannot be null or empty"));
        }
        return findById(id)
                .then(exerciseRepository.deleteById(id))
                .doOnSuccess(v -> log.debug("Deleted exercise with id: {}", id));
    }

    public Flux<Exercise> findAll() {
        return exerciseRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all exercises"));
    }
}