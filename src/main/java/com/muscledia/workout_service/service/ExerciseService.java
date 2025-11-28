package com.muscledia.workout_service.service;

import com.muscledia.workout_service.exception.ResourceNotFoundException;
import com.muscledia.workout_service.exception.ValidationException;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.repository.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


/**
 * Service for Exercise operations
 *
 * BUSINESS LOGIC LAYER:
 * - Validates inputs
 * - Handles error cases
 * - Provides clean interface to controllers
 *
 * ENHANCED FILTERING:
 * Added support for category and bodyPart filtering
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExerciseService {
    private final ExerciseRepository exerciseRepository;

    // ==================== BASIC OPERATIONS ====================

    public Mono<Exercise> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.error(new ValidationException("id", "Exercise ID cannot be null or empty"));
        }
        return exerciseRepository.findById(id)
                .doOnNext(exercise -> log.debug("Found exercise: {}", exercise.getName()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Exercise", "id", id)));
    }

    public Mono<Exercise> findByExternalId(String externalId) {
        if (externalId == null || externalId.trim().isEmpty()) {
            return Mono.error(new ValidationException("externalId", "External ID cannot be null or empty"));
        }
        return exerciseRepository.findByExternalId(externalId)
                .doOnNext(exercise -> log.debug("Found exercise by external ID: {}", exercise.getName()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Exercise", "externalId", externalId)));
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

    public Flux<Exercise> findAllPaginated(Pageable pageable) {
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findAllBy(pageable)
                .doOnComplete(() -> log.debug("Retrieved all exercises (paginated)"));
    }

    // ==================== DIFFICULTY FILTERS ====================

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

    public Mono<Long> countByDifficulty(ExerciseDifficulty difficulty) {
        if (difficulty == null) {
            return Mono.error(new ValidationException("difficulty", "Difficulty level cannot be null"));
        }
        return exerciseRepository.countByDifficulty(difficulty)
                .doOnSuccess(count -> log.debug("Counted {} exercises for difficulty: {}", count, difficulty));
    }

    // ==================== CATEGORY FILTERS (NEW) ====================

    public Flux<Exercise> findByCategory(ExerciseCategory category) {
        if (category == null) {
            return Flux.error(new ValidationException("category", "Category cannot be null"));
        }
        return exerciseRepository.findByCategory(category)
                .doOnComplete(() -> log.debug("Retrieved exercises for category: {}", category));
    }

    public Flux<Exercise> findByCategoryPaginated(ExerciseCategory category, Pageable pageable) {
        if (category == null) {
            return Flux.error(new ValidationException("category", "Category cannot be null"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByCategory(category, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for category: {}", category));
    }

    public Flux<Exercise> findByCategoryAndDifficulty(ExerciseCategory category, ExerciseDifficulty difficulty) {
        if (category == null) {
            return Flux.error(new ValidationException("category", "Category cannot be null"));
        }
        if (difficulty == null) {
            return Flux.error(new ValidationException("difficulty", "Difficulty cannot be null"));
        }
        return exerciseRepository.findByCategoryAndDifficulty(category, difficulty)
                .doOnComplete(() -> log.debug("Retrieved exercises for category {} and difficulty {}", category, difficulty));
    }

    public Flux<Exercise> findByCategoryAndDifficultyPaginated(
            ExerciseCategory category, ExerciseDifficulty difficulty, Pageable pageable) {
        if (category == null) {
            return Flux.error(new ValidationException("category", "Category cannot be null"));
        }
        if (difficulty == null) {
            return Flux.error(new ValidationException("difficulty", "Difficulty cannot be null"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByCategoryAndDifficulty(category, difficulty, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for category {} and difficulty {}",
                        category, difficulty));
    }

    // ==================== BODY PART FILTERS (NEW) ====================

    public Flux<Exercise> findByBodyPart(String bodyPart) {
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return Flux.error(new ValidationException("bodyPart", "Body part cannot be null or empty"));
        }
        return exerciseRepository.findByBodyPart(bodyPart)
                .doOnComplete(() -> log.debug("Retrieved exercises for body part: {}", bodyPart));
    }

    public Flux<Exercise> findByBodyPartPaginated(String bodyPart, Pageable pageable) {
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return Flux.error(new ValidationException("bodyPart", "Body part cannot be null or empty"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByBodyPart(bodyPart, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for body part: {}", bodyPart));
    }

    public Flux<Exercise> findByBodyPartAndEquipment(String bodyPart, String equipment) {
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return Flux.error(new ValidationException("bodyPart", "Body part cannot be null or empty"));
        }
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        return exerciseRepository.findByBodyPartAndEquipment(bodyPart, equipment)
                .doOnComplete(() -> log.debug("Retrieved exercises for body part {} and equipment {}", bodyPart, equipment));
    }

    public Flux<Exercise> findByBodyPartAndEquipmentPaginated(String bodyPart, String equipment, Pageable pageable) {
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return Flux.error(new ValidationException("bodyPart", "Body part cannot be null or empty"));
        }
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByBodyPartAndEquipment(bodyPart, equipment, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for body part {} and equipment {}",
                        bodyPart, equipment));
    }

    public Flux<Exercise> findByCategoryAndBodyPart(ExerciseCategory category, String bodyPart) {
        if (category == null) {
            return Flux.error(new ValidationException("category", "Category cannot be null"));
        }
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return Flux.error(new ValidationException("bodyPart", "Body part cannot be null or empty"));
        }
        return exerciseRepository.findByCategoryAndBodyPart(category, bodyPart)
                .doOnComplete(() -> log.debug("Retrieved exercises for category {} and body part {}", category, bodyPart));
    }

    public Flux<Exercise> findByCategoryAndBodyPartPaginated(
            ExerciseCategory category, String bodyPart, Pageable pageable) {
        if (category == null) {
            return Flux.error(new ValidationException("category", "Category cannot be null"));
        }
        if (bodyPart == null || bodyPart.trim().isEmpty()) {
            return Flux.error(new ValidationException("bodyPart", "Body part cannot be null or empty"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByCategoryAndBodyPart(category, bodyPart, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for category {} and body part {}",
                        category, bodyPart));
    }

    // ==================== EQUIPMENT FILTERS ====================

    public Flux<Exercise> findByEquipment(String equipment) {
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        return exerciseRepository.findByEquipment(equipment)
                .doOnComplete(() -> log.debug("Retrieved exercises for equipment: {}", equipment));
    }

    public Flux<Exercise> findByEquipmentPaginated(String equipment, Pageable pageable) {
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByEquipment(equipment, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for equipment: {}", equipment));
    }

    public Flux<Exercise> findByEquipmentTypes(List<String> equipmentList) {
        return exerciseRepository.findByEquipmentIn(equipmentList)
                .doOnComplete(() -> log.debug("Retrieved exercises for equipment types: {}", equipmentList));
    }

    // ==================== MUSCLE FILTERS ====================

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

    public Flux<Exercise> findByMuscleGroupPaginated(String muscleGroup, Pageable pageable) {
        if (muscleGroup == null || muscleGroup.trim().isEmpty()) {
            return Flux.error(new ValidationException("muscleGroup", "Muscle group cannot be null or empty"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByMuscleGroupPaginated(muscleGroup, pageable)
                .doOnComplete(() -> log.debug("Retrieved paginated exercises for muscle group: {}", muscleGroup));
    }

    public Flux<Exercise> findByMuscleGroupAndEquipment(String muscleGroup, String equipment, Pageable pageable) {
        if (muscleGroup == null || muscleGroup.trim().isEmpty()) {
            return Flux.error(new ValidationException("muscleGroup", "Muscle group cannot be null or empty"));
        }
        if (equipment == null || equipment.trim().isEmpty()) {
            return Flux.error(new ValidationException("equipment", "Equipment cannot be null or empty"));
        }
        if (pageable == null) {
            return Flux.error(new ValidationException("pageable", "Pageable cannot be null"));
        }
        return exerciseRepository.findByMuscleGroupAndEquipment(muscleGroup, equipment, pageable)
                .doOnComplete(() -> log.debug("Retrieved exercises for muscle group: {} and equipment: {} (paginated)",
                        muscleGroup, equipment));
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

    // ==================== SEARCH ====================

    public Flux<Exercise> searchByName(String name) {
        return exerciseRepository.findByNameContainingIgnoreCase(name)
                .doOnComplete(() -> log.debug("Retrieved exercises matching name: {}", name));
    }

    public Flux<Exercise> searchByNameAndDifficulty(String name, ExerciseDifficulty difficulty) {
        return exerciseRepository.findByNameContainingIgnoreCaseAndDifficulty(name, difficulty)
                .doOnComplete(
                        () -> log.debug("Retrieved exercises matching name and difficulty: {}, {}", name, difficulty));
    }

    // ==================== SPECIAL FILTERS ====================

    public Flux<Exercise> findBodyweightExercises() {
        return exerciseRepository.findBodyweightExercises()
                .doOnComplete(() -> log.debug("Retrieved bodyweight exercises"));
    }
}