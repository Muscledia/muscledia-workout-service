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

    // Common muscle groups from ExerciseDB
    private final List<String> commonMuscleGroups = Arrays.asList(
            "biceps", "triceps", "chest", "back", "shoulders", "legs",
            "glutes", "abs", "cardio", "calves", "forearms", "lats",
            "middle back", "lower back", "hamstrings", "quadriceps");

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder.baseUrl(exerciseApiUrl).build();
    }

    public Mono<Void> fetchAndPopulateMuscleGroups() {
        log.info("Starting muscle group data population");

        return Flux.fromIterable(commonMuscleGroups)
                .flatMap(this::fetchMuscleGroupFromApi)
                .flatMap(this::saveMuscleGroup)
                .doOnComplete(() -> log.info("Completed muscle group data population"))
                .doOnError(error -> log.error("Error fetching muscle group data", error))
                .then();
    }

    /**
     * Find or create a muscle group by name - used by ExerciseDataService
     */
    public Mono<MuscleGroup> findOrCreateMuscleGroup(String muscleName) {
        return muscleGroupRepository.findByName(muscleName)
                .switchIfEmpty(createNewMuscleGroup(muscleName));
    }

    /**
     * Create a new muscle group - used internally and by ExerciseDataService
     */
    public Mono<MuscleGroup> createNewMuscleGroup(String muscleName) {
        MuscleGroup newMuscleGroup = new MuscleGroup();
        newMuscleGroup.setName(muscleName);
        newMuscleGroup.setCreatedAt(LocalDateTime.now());
        log.info("Creating new muscle group: {}", muscleName);
        return muscleGroupRepository.save(newMuscleGroup);
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
        MuscleGroup muscleGroup = new MuscleGroup();
        muscleGroup.setName(capitalizeFirst(muscleName));
        muscleGroup.setDescription("Muscle group with " +
                (response.getData() != null ? response.getData().getExercises().size() : 0) +
                " associated exercises");
        muscleGroup.setCreatedAt(LocalDateTime.now());
        return muscleGroup;
    }

    private MuscleGroup createBasicMuscleGroup(String muscleName) {
        MuscleGroup muscleGroup = new MuscleGroup();
        muscleGroup.setName(capitalizeFirst(muscleName));
        muscleGroup.setDescription("Basic muscle group entry for " + muscleName);
        muscleGroup.setCreatedAt(LocalDateTime.now());
        return muscleGroup;
    }

    private Mono<MuscleGroup> saveMuscleGroup(MuscleGroup muscleGroup) {
        return muscleGroupRepository.findByName(muscleGroup.getName())
                .doOnNext(existing -> log.debug("Muscle group already exists: {}", existing.getName()))
                .switchIfEmpty(muscleGroupRepository.save(muscleGroup)
                        .doOnNext(saved -> log.info("Created muscle group: {}", saved.getName())));
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}