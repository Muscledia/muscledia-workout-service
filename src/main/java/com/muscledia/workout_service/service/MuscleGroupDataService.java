package com.muscledia.workout_service.service;

import com.muscledia.workout_service.external.exercisedb.dto.ExerciseApiResponse;
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
    private final WebClient.Builder webClientBuilder;

    @Value("${api.exercise.url}")
    private String exerciseApiUrl;

    private WebClient webClient;

    // Updated target muscles from actual ExerciseDB API response (50 muscles!)
    private final List<String> targetMuscles = Arrays.asList(
            "shins", "hands", "wrists", "latissimus dorsi", "grip muscles",
            "sternocleidomastoid", "wrist extensors", "wrist flexors", "brachialis",
            "lower abs", "rotator cuff", "inner thighs", "ankles", "feet", "groin",
            "deltoids", "upper chest", "trapezius", "chest", "rear deltoids",
            "shoulders", "ankle stabilizers", "rhomboids", "core", "hip flexors",
            "lower back", "obliques", "serratus anterior", "abductors", "levator scapulae",
            "traps", "upper back", "biceps", "forearms", "adductors", "spine",
            "triceps", "cardiovascular system", "quads", "lats", "glutes",
            "pectorals", "abs", "delts", "calves", "hamstrings", "soleus",
            "abdominals", "back", "quadriceps");

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder.baseUrl(exerciseApiUrl).build();
    }

    /**
     * Fetch target muscles list from ExerciseDB API and populate muscle groups
     * This now handles the comprehensive 50+ muscle groups from the API
     */
    public Mono<Void> fetchTargetMusclesFromApi() {
        log.info("Fetching comprehensive target muscles from ExerciseDB API");

        return webClient.get()
                .uri("/muscles")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    // Parse the JSON response to extract muscle names
                    // The response format: {"success": true, "data": [{"name": "muscle_name"},
                    // ...]}
                    log.debug("Raw API response: {}", response);
                    return response;
                })
                .flatMapMany(response -> {
                    // For now, use the predefined list since we have the actual data
                    // In a real implementation, you'd parse the JSON here
                    return Flux.fromIterable(targetMuscles);
                })
                .flatMap(muscleName -> {
                    String normalizedName = normalizeMuscleGroupName(muscleName);
                    return findOrCreateMuscleGroup(normalizedName)
                            .doOnNext(mg -> log.debug("Created/found muscle group: {}", mg.getName()));
                })
                .doOnComplete(() -> log.info("Completed comprehensive target muscles population from API"))
                .doOnError(error -> log.error("Error fetching target muscles from API", error))
                .then();
    }

    public Mono<Void> fetchAndPopulateMuscleGroups() {
        log.info("Starting muscle group data population using target muscles");

        return Flux.fromIterable(targetMuscles)
                .flatMap(this::fetchMuscleGroupFromApi)
                .flatMap(this::saveMuscleGroup)
                .doOnComplete(() -> log.info("Completed muscle group data population"))
                .doOnError(error -> log.error("Error fetching muscle group data", error))
                .then();
    }

    /**
     * Find or create a muscle group by name - used by ExerciseDataService
     * Normalizes the name to prevent duplicates and handles potential duplicate
     * results gracefully
     */
    public Mono<MuscleGroup> findOrCreateMuscleGroup(String muscleName) {
        String normalizedName = normalizeMuscleGroupName(muscleName);
        return muscleGroupRepository.findFirstByName(normalizedName) // Use safer method
                .onErrorResume(error -> {
                    log.warn("Error finding muscle group '{}': {}. Attempting to create new one.", normalizedName,
                            error.getMessage());
                    return createNewMuscleGroup(normalizedName);
                })
                .switchIfEmpty(createNewMuscleGroup(normalizedName));
    }

    /**
     * Create a new muscle group - used internally and by ExerciseDataService
     */
    public Mono<MuscleGroup> createNewMuscleGroup(String muscleName) {
        String normalizedName = normalizeMuscleGroupName(muscleName);
        MuscleGroup newMuscleGroup = new MuscleGroup();
        newMuscleGroup.setName(normalizedName);
        newMuscleGroup.setCreatedAt(LocalDateTime.now());
        log.info("Creating new muscle group: {}", normalizedName);
        return muscleGroupRepository.save(newMuscleGroup);
    }

    /**
     * Normalize muscle group names to prevent duplicates
     * Handles the comprehensive 50+ muscle groups from ExerciseDB API
     */
    private String normalizeMuscleGroupName(String muscleName) {
        if (muscleName == null || muscleName.trim().isEmpty()) {
            return "unknown";
        }

        // Convert to lowercase and trim
        String normalized = muscleName.toLowerCase().trim();

        // Handle variations and aliases - prefer the most specific/common name
        switch (normalized) {
            // Chest muscles - prefer "pectorals" (more specific)
            case "chest":
            case "pecs":
                return "pectorals";
            case "upper chest":
                return "upper chest"; // Keep specific variation

            // Shoulder muscles - prefer "delts" (common gym terminology)
            case "deltoids":
            case "shoulders":
                return "delts";
            case "rear deltoids":
                return "rear delts"; // Normalize to shorter form

            // Back muscles
            case "latissimus dorsi":
                return "lats";
            case "back":
                return "upper back"; // Default broad "back" to upper back
            case "trapezius":
                return "traps";

            // Core/abs muscles - prefer "abs" (most common)
            case "abdominals":
            case "core":
                return "abs";
            case "lower abs":
                return "lower abs"; // Keep specific variation

            // Leg muscles
            case "quadriceps":
            case "quadriceps femoris":
                return "quads";
            case "inner thighs":
                return "adductors"; // More anatomically correct
            case "gluteus maximus":
            case "gluteal":
                return "glutes";

            // Arm muscles
            case "biceps brachii":
                return "biceps";
            case "triceps brachii":
                return "triceps";

            // Calf muscles
            case "gastrocnemius":
                return "calves";
            case "soleus":
                return "soleus"; // Keep specific calf muscle

            // Specialized muscles - keep as is but clean up
            case "sternocleidomastoid":
                return "sternocleidomastoid";
            case "brachialis":
                return "brachialis";
            case "serratus anterior":
                return "serratus anterior";
            case "levator scapulae":
                return "levator scapulae";
            case "rhomboids":
                return "rhomboids";
            case "rotator cuff":
                return "rotator cuff";

            // Functional groups
            case "grip muscles":
                return "grip muscles";
            case "ankle stabilizers":
                return "ankle stabilizers";
            case "hip flexors":
                return "hip flexors";

            // Cardiovascular
            case "cardiovascular system":
                return "cardio"; // Shorter, more practical

            // Keep specific body parts as is
            case "wrist extensors":
            case "wrist flexors":
            case "forearms":
            case "hands":
            case "wrists":
            case "ankles":
            case "feet":
            case "shins":
            case "groin":
            case "spine":
            case "obliques":
            case "abductors":
            case "adductors":
            case "lower back":
            case "upper back":
                return normalized; // Keep as is

            default:
                return normalized;
        }
    }

    /**
     * Clean up duplicate muscle groups in the database
     */
    public Mono<Void> cleanupDuplicateMuscleGroups() {
        log.info("Starting cleanup of duplicate muscle groups");

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
                .doOnSuccess(v -> log.info("Completed muscle group cleanup"));
    }

    private Mono<MuscleGroup> fetchMuscleGroupFromApi(String muscleName) {
        return webClient.get()
                .uri("/muscles/{muscleName}/exercises", muscleName)
                .retrieve()
                .bodyToMono(ExerciseApiResponse.class)
                .map(response -> createMuscleGroupFromApi(muscleName, response))
                .onErrorResume(error -> {
                    log.warn("Error fetching data for muscle: {}, creating basic muscle group", muscleName);
                    return Mono.just(createBasicMuscleGroup(muscleName));
                });
    }

    private MuscleGroup createMuscleGroupFromApi(String muscleName, ExerciseApiResponse response) {
        String normalizedName = normalizeMuscleGroupName(muscleName);
        MuscleGroup muscleGroup = new MuscleGroup();
        muscleGroup.setName(normalizedName);
        muscleGroup.setDescription("Muscle group with " +
                (response.getData() != null ? response.getData().getExercises().size() : 0) +
                " associated exercises");
        muscleGroup.setCreatedAt(LocalDateTime.now());
        return muscleGroup;
    }

    private MuscleGroup createBasicMuscleGroup(String muscleName) {
        String normalizedName = normalizeMuscleGroupName(muscleName);
        MuscleGroup muscleGroup = new MuscleGroup();
        muscleGroup.setName(normalizedName);
        muscleGroup.setDescription("Basic muscle group entry for " + normalizedName);
        muscleGroup.setCreatedAt(LocalDateTime.now());
        return muscleGroup;
    }

    private Mono<MuscleGroup> saveMuscleGroup(MuscleGroup muscleGroup) {
        return muscleGroupRepository.findByName(muscleGroup.getName())
                .doOnNext(existing -> log.debug("Muscle group already exists: {}", existing.getName()))
                .switchIfEmpty(muscleGroupRepository.save(muscleGroup)
                        .doOnNext(saved -> log.info("Created muscle group: {}", saved.getName())));
    }
}