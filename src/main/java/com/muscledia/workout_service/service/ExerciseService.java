package com.muscledia.workout_service.service;

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
        return exerciseRepository.findById(id)
                .doOnNext(exercise -> log.debug("Found exercise: {}", exercise.getName()))
                .switchIfEmpty(Mono.error(new RuntimeException("Exercise not found with id: " + id)));
    }

    public Mono<Exercise> findByExternalApiId(String externalApiId) {
        return exerciseRepository.findByExternalApiId(externalApiId)
                .doOnNext(exercise -> log.debug("Found exercise by external API ID: {}", exercise.getName()));
    }

    public Flux<Exercise> findByDifficulty(ExerciseDifficulty difficulty) {
        return exerciseRepository.findByDifficulty(difficulty)
                .doOnComplete(() -> log.debug("Retrieved exercises for difficulty: {}", difficulty));
    }

    public Flux<Exercise> findByDifficultyPaginated(ExerciseDifficulty difficulty, Pageable pageable) {
        return exerciseRepository.findByDifficulty(difficulty, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for difficulty: {}", difficulty));
    }

    public Flux<Exercise> findByEquipment(String equipment) {
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
        return exerciseRepository.countByDifficulty(difficulty)
                .doOnSuccess(count -> log.debug("Counted {} exercises for difficulty: {}", count, difficulty));
    }

    public Flux<Exercise> findExercisesWithAnimations() {
        return exerciseRepository.findByAnimationUrlIsNotNull()
                .doOnComplete(() -> log.debug("Retrieved exercises with animations"));
    }

    public Flux<Exercise> findByDifficultyAndMuscle(ExerciseDifficulty difficulty, String muscle) {
        return exerciseRepository.findByDifficultyAndMuscleInvolved(difficulty, muscle)
                .doOnComplete(
                        () -> log.debug("Retrieved exercises for difficulty and muscle: {}, {}", difficulty, muscle));
    }

    public Mono<Exercise> save(Exercise exercise) {
        return exerciseRepository.save(exercise)
                .doOnSuccess(saved -> log.debug("Saved exercise: {}", saved.getName()));
    }

    public Mono<Void> deleteById(String id) {
        return exerciseRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted exercise with id: {}", id));
    }

    public Flux<Exercise> findAll() {
        return exerciseRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all exercises"));
    }
}