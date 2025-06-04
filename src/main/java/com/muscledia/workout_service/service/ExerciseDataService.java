package com.muscledia.workout_service.service;

import com.muscledia.workout_service.external.exercisedb.dto.ExerciseApiResponse;
import com.muscledia.workout_service.external.exercisedb.dto.ExerciseData;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.MuscleGroup;
import com.muscledia.workout_service.model.embedded.MuscleGroupRef;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.repository.ExerciseRepository;
import com.muscledia.workout_service.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExerciseDataService {

    private final ExerciseRepository exerciseRepository;
    private final MuscleGroupRepository muscleGroupRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${api.exercise.url}")
    private String exerciseApiUrl;

    // Pre-build the WebClient to avoid creating it repeatedly inside
    // fetchAndPopulateExercises
    private WebClient webClient;

    // Use a PostConstruct method to initialize the WebClient after @Value is
    // injected
    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = webClientBuilder.baseUrl(exerciseApiUrl).build();
    }

    public Mono<Void> fetchAndPopulateExercises() {
        log.info("Starting exercise data population from API");

        return webClient.get()
                .uri("/exercises")
                .retrieve()
                // CHANGE HERE: Map to the new top-level DTO
                .bodyToMono(ExerciseApiResponse.class) // The root is a single object, not an array
                .flatMapMany(response -> { // Use flatMapMany because we'll get a Mono<List<ExerciseData>> and want to flatten to Flux<ExerciseData>
                    if (response.isSuccess() && response.getData() != null && response.getData().getExercises() != null) {
                        log.info("API call successful. Found {} exercises.", response.getData().getExercises().size());
                        // Convert the List<ExerciseData> into a Flux<ExerciseData> to process each one
                        return Flux.fromIterable(response.getData().getExercises());
                    } else {
                        log.warn("API response was not successful or contained no exercise data: {}", response);
                        return Flux.empty(); // Return empty Flux if no data or not successful
                    }
                })
                .flatMap(this::processAndSaveExercise) // Process and save each exercise reactively
                .doOnComplete(() -> log.info("All exercises from API processed (initial stream complete)."))
                .doOnError(error -> log.error("Error fetching or processing exercise data from API", error))
                .collectList() // Collect all Mono<Exercise> results into a single list
                .doOnSuccess(list -> log.info("Successfully processed and saved {} exercises.", list.size()))
                .doOnError(error -> log.error("Error during final collection/saving of exercises.", error))
                .then(); // Signal completion only after all saves are done
    }

    private Mono<Exercise> processAndSaveExercise(ExerciseData data) {
        Exercise exercise = convertToExercise(data);

        return exerciseRepository.findByExternalApiId(exercise.getExternalApiId())
                .flatMap(existingExercise -> {
                    log.debug("Exercise already exists, skipping save for: {}", existingExercise.getName());
                    return Mono.just(existingExercise);
                })
                .switchIfEmpty(processNewExercise(exercise));
    }

    private Mono<Exercise> processNewExercise(Exercise exercise) {
        List<Mono<MuscleGroupRef>> muscleGroupRefMonos = exercise.getMuscleGroups().stream()
                .map(ref -> findOrCreateMuscleGroup(ref.getName())
                        .map(mg -> {
                            ref.setMuscleId(mg.getId());
                            return ref;
                        }))
                .collect(Collectors.toList());

        return Flux.merge(muscleGroupRefMonos)
                .collectList()
                .map(updatedRefs -> {
                    exercise.setMuscleGroups(updatedRefs);
                    exercise.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
                    exercise.setUpdatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
                    return exercise;
                })
                .flatMap(exerciseRepository::save)
                .onErrorResume(e -> { // Changed to onErrorResume to allow stream to continue on individual errors
                    log.error("Error saving new exercise {}: {}", exercise.getName(), e.getMessage());
                    return Mono.empty(); // Return empty to effectively skip this exercise on save failure
                });
    }

    private Mono<MuscleGroup> findOrCreateMuscleGroup(String muscleName) {
        return muscleGroupRepository.findByName(muscleName)
                .switchIfEmpty(createNewMuscleGroup(muscleName));
    }

    private Mono<MuscleGroup> createNewMuscleGroup(String muscleName) {
        MuscleGroup newMuscleGroup = new MuscleGroup();
        newMuscleGroup.setName(muscleName);
        newMuscleGroup.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())); // Consistent with Instant
        log.info("Creating new muscle group: {}", muscleName);
        return muscleGroupRepository.save(newMuscleGroup);
    }

    private Exercise convertToExercise(ExerciseData data) {
        Exercise exercise = new Exercise();
        exercise.setExternalApiId(data.getExerciseId());
        exercise.setName(data.getName());

        // Handle nulls for instructions and equipments defensively
        exercise.setDescription(data.getInstructions() != null && !data.getInstructions().isEmpty()
                ? String.join("\n", data.getInstructions())
                : "");
        exercise.setEquipment(data.getEquipments() != null && !data.getEquipments().isEmpty()
                ? String.join(", ", data.getEquipments())
                : "");
        exercise.setAnimationUrl(data.getGifUrl());

        exercise.setDifficulty(ExerciseDifficulty.INTERMEDIATE);

        if (data.getTargetMuscles() != null && !data.getTargetMuscles().isEmpty()) {
            exercise.setTargetMuscle(data.getTargetMuscles().get(0)); // Only sets the first target muscle as "targetMuscle" field
        } else {
            exercise.setTargetMuscle(null); // Explicitly set to null if no target muscles
        }

        // Use a Map to ensure unique muscle group references per exercise,
        // and correctly handle primary/secondary status.
        Map<String, MuscleGroupRef> uniqueMuscleGroupRefs = new HashMap<>();

        // Process target muscles (they are primary)
        if (data.getTargetMuscles() != null) {
            data.getTargetMuscles().forEach(muscleName -> {
                MuscleGroupRef ref = new MuscleGroupRef();
                ref.setName(muscleName);
                ref.setPrimary(true); // Target muscles are primary
                uniqueMuscleGroupRefs.put(muscleName, ref); // Add to map, new entry or replaces existing if secondary was added first
            });
        }

        // Process secondary muscles
        if (data.getSecondaryMuscles() != null) {
            data.getSecondaryMuscles().forEach(muscleName -> {
                // If the muscle is already in the map as a target (primary), keep it as primary.
                // Otherwise, add it as a secondary muscle.
                uniqueMuscleGroupRefs.computeIfAbsent(muscleName, k -> {
                    MuscleGroupRef ref = new MuscleGroupRef();
                    ref.setName(muscleName);
                    ref.setPrimary(false); // Secondary muscles are not primary
                    return ref;
                });
            });
        }

        exercise.setMuscleGroups(new ArrayList<>(uniqueMuscleGroupRefs.values()));
        return exercise;
    }
}