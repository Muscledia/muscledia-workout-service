package com.muscledia.workout_service.service;

import com.muscledia.workout_service.model.MuscleGroup;
import com.muscledia.workout_service.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MuscleGroupDataService {

    private final MuscleGroupRepository muscleGroupRepository;

    // Comprehensive list of muscle groups from ExerciseDB
    private final List<String> targetMuscles = Arrays.asList(
            "abs", "adductors", "abductors", "biceps", "calves", "cardio",
            "delts", "forearms", "glutes", "hamstrings", "hip flexors",
            "lats", "lower back", "lower abs", "obliques", "pectorals",
            "quads", "traps", "triceps", "upper back", "upper chest",
            "rear delts", "rhomboids", "rotator cuff", "serratus anterior",
            "levator scapulae", "brachialis", "wrist extensors", "wrist flexors",
            "grip muscles", "ankle stabilizers", "soleus", "spine",
            "sternocleidomastoid", "shins", "hands", "wrists", "ankles",
            "feet", "groin"
    );

    /**
     * Populate muscle groups from predefined list
     * Call this after exercise migration to ensure all muscle groups exist
     */
    public Mono<Void> populateMuscleGroups() {
        log.info("🏋️ Starting muscle group population");

        return Flux.fromIterable(targetMuscles)
                .flatMap(this::findOrCreateMuscleGroup)
                .doOnNext(mg -> log.debug("✅ Created/found muscle group: {}", mg.getName()))
                .doOnComplete(() -> log.info("✅ Completed muscle group population"))
                .doOnError(error -> log.error("❌ Error populating muscle groups", error))
                .then();
    }

    /**
     * Find or create a muscle group by name
     * Used by exercise service when needed
     */
    public Mono<MuscleGroup> findOrCreateMuscleGroup(String muscleName) {
        String normalizedName = normalizeMuscleGroupName(muscleName);

        return muscleGroupRepository.findByName(normalizedName)
                .doOnNext(mg -> log.debug("Found existing muscle group: {}", normalizedName))
                .switchIfEmpty(
                        Mono.defer(() -> createNewMuscleGroup(normalizedName))
                                .doOnNext(mg -> log.info("Created new muscle group: {}", normalizedName))
                )
                .onErrorResume(error -> {
                    log.warn("Error with muscle group '{}': {}. Creating new one.",
                            normalizedName, error.getMessage());
                    return createNewMuscleGroup(normalizedName);
                });
    }

    /**
     * Create a new muscle group
     */
    public Mono<MuscleGroup> createNewMuscleGroup(String muscleName) {
        String normalizedName = normalizeMuscleGroupName(muscleName);

        MuscleGroup newMuscleGroup = new MuscleGroup();
        newMuscleGroup.setName(normalizedName);
        newMuscleGroup.setDescription("Muscle group: " + normalizedName);
        newMuscleGroup.setCreatedAt(LocalDateTime.now());

        return muscleGroupRepository.save(newMuscleGroup);
    }

    /**
     * Normalize muscle group names to prevent duplicates
     */
    private String normalizeMuscleGroupName(String muscleName) {
        if (muscleName == null || muscleName.trim().isEmpty()) {
            return "unknown";
        }

        String normalized = muscleName.toLowerCase().trim();

        // Map common variations to standard names
        return switch (normalized) {
            // Chest
            case "chest", "pecs" -> "pectorals";
            case "upper chest" -> "upper chest";

            // Shoulders
            case "deltoids", "shoulders" -> "delts";
            case "rear deltoids" -> "rear delts";

            // Back
            case "latissimus dorsi" -> "lats";
            case "back" -> "upper back";
            case "trapezius" -> "traps";

            // Core
            case "abdominals", "core" -> "abs";
            case "lower abs" -> "lower abs";

            // Legs
            case "quadriceps", "quadriceps femoris" -> "quads";
            case "inner thighs" -> "adductors";
            case "gluteus maximus", "gluteal" -> "glutes";

            // Arms
            case "biceps brachii" -> "biceps";
            case "triceps brachii" -> "triceps";

            // Calves
            case "gastrocnemius" -> "calves";
            case "soleus" -> "soleus";

            // Cardiovascular
            case "cardiovascular system" -> "cardio";

            // Keep everything else as-is
            default -> normalized;
        };
    }

    /**
     * Clean up duplicate muscle groups in the database
     */
    public Mono<Void> cleanupDuplicateMuscleGroups() {
        log.info("🧹 Starting cleanup of duplicate muscle groups");

        return muscleGroupRepository.findAll()
                .groupBy(mg -> normalizeMuscleGroupName(mg.getName()))
                .flatMap(group -> {
                    String normalizedName = group.key();
                    return group.collectList()
                            .flatMap(muscleGroups -> {
                                if (muscleGroups.size() > 1) {
                                    // Keep the first one, delete the rest
                                    MuscleGroup toKeep = muscleGroups.get(0);
                                    toKeep.setName(normalizedName);

                                    List<MuscleGroup> toDelete = muscleGroups.subList(1, muscleGroups.size());
                                    log.info("Found {} duplicates for '{}', keeping ID: {}",
                                            toDelete.size(), normalizedName, toKeep.getId());

                                    return muscleGroupRepository.save(toKeep)
                                            .then(Flux.fromIterable(toDelete)
                                                    .flatMap(muscleGroupRepository::delete)
                                                    .then());
                                }
                                return Mono.empty();
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("✅ Completed muscle group cleanup"));
    }

    /**
     * Get all muscle groups
     */
    public Flux<MuscleGroup> getAllMuscleGroups() {
        return muscleGroupRepository.findAll();
    }

    /**
     * Count muscle groups
     */
    public Mono<Long> countMuscleGroups() {
        return muscleGroupRepository.count();
    }
}