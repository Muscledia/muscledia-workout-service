package com.muscledia.workout_service.service;

import com.muscledia.workout_service.external.hevy.dto.HevyApiResponse;
import com.muscledia.workout_service.external.hevy.dto.HevyRoutineFolderResponse;
import com.muscledia.workout_service.external.hevy.dto.HevyRoutineFolderResponse.HevyRoutineFolder;
import com.muscledia.workout_service.external.hevy.dto.HevyWorkoutRoutine;
import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.model.RoutineFolder;
import com.muscledia.workout_service.model.embedded.PlannedExercise;
import com.muscledia.workout_service.repository.WorkoutPlanRepository;
import com.muscledia.workout_service.repository.RoutineFolderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HevyDataService {
    private final WorkoutPlanRepository workoutPlanRepository;
    private final RoutineFolderRepository routineFolderRepository;
    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    @Value("${api.hevy.base-url}")
    private String hevyBaseUrl;

    @Value("${api.hevy.api-key}")
    private String hevyApiKey;

    @Value("${api.hevy.version:v1}")
    private String apiVersion;

    @Value("${api.hevy.routines-page-size:10}")
    private Integer routinesPageSize;

    @Value("${api.hevy.routine-folders-page-size:10}")
    private Integer routineFoldersPageSize;

    @Value("${api.hevy.max-routines-pages:6}")
    private Integer maxRoutinesPages;

    @Value("${api.hevy.max-routine-folders-pages:2}")
    private Integer maxRoutineFoldersPages;

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder
                .baseUrl(hevyBaseUrl)
                .defaultHeader("api-key", hevyApiKey)
                .defaultHeader("accept", "application/json")
                .build();
    }

    public Mono<Void> fetchAndPopulateAllData() {
        return Mono.when(
                fetchAndPopulateRoutines(),
                fetchAndPopulateRoutineFolders());
    }

    public Mono<Void> fetchAndPopulateRoutines() {
        return Flux.range(1, maxRoutinesPages)
                .flatMap(this::fetchRoutinesPage)
                .flatMap(this::populateWorkoutPlans)
                .then();
    }

    public Mono<Void> fetchAndPopulateRoutineFolders() {
        return Flux.range(1, maxRoutineFoldersPages)
                .flatMap(this::fetchRoutineFoldersPage)
                .doOnNext(response -> log.info("Fetched {} folders from page {}",
                        response.getFolders().size(), response.getPage()))
                .flatMapIterable(HevyRoutineFolderResponse::getFolders)
                .map(this::convertToRoutineFolder)
                .flatMap(routineFolderRepository::save)
                .doOnNext(folder -> log.info("Saved routine folder: {}", folder.getTitle()))
                .then();
    }

    private Mono<HevyApiResponse> fetchRoutinesPage(int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{version}/routines")
                        .queryParam("page", page)
                        .queryParam("pageSize", routinesPageSize)
                        .build(apiVersion))
                .retrieve()
                .bodyToMono(HevyApiResponse.class)
                .doOnNext(response -> log.info("Fetched {} routines from page {}",
                        response.getRoutines().size(), response.getPage()));
    }

    private Mono<HevyRoutineFolderResponse> fetchRoutineFoldersPage(int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{version}/routine_folders")
                        .queryParam("page", page)
                        .queryParam("pageSize", routineFoldersPageSize)
                        .build(apiVersion))
                .retrieve()
                .bodyToMono(HevyRoutineFolderResponse.class);
    }

    private RoutineFolder convertToRoutineFolder(HevyRoutineFolder hevyFolder) {
        RoutineFolder folder = new RoutineFolder();
        folder.setHevyId(hevyFolder.getId());
        folder.setTitle(hevyFolder.getTitle());
        folder.setFolderIndex(hevyFolder.getFolderIndex());
        folder.setCreatedAt(hevyFolder.getCreatedAt());
        folder.setUpdatedAt(hevyFolder.getUpdatedAt());
        return folder;
    }

    public Mono<Void> populateWorkoutPlans(HevyApiResponse hevyApiResponse) {
        return Flux.fromIterable(hevyApiResponse.getRoutines())
                .map(this::convertToWorkoutPlan)
                .flatMap(workoutPlanRepository::save)
                .doOnComplete(
                        () -> log.info("Successfully populated {} workout plans", hevyApiResponse.getRoutines().size()))
                .then();
    }

    private WorkoutPlan convertToWorkoutPlan(HevyWorkoutRoutine routine) {
        WorkoutPlan workoutPlan = new WorkoutPlan();
        workoutPlan.setId(routine.getId());
        workoutPlan.setTitle(routine.getTitle());
        workoutPlan.setDescription("Imported from Hevy");
        workoutPlan.setFolderId(routine.getFolderId());

        // Convert exercises
        List<PlannedExercise> plannedExercises = routine.getExercises().stream()
                .map(this::convertToPlannedExercise)
                .collect(Collectors.toList());
        workoutPlan.setExercises(plannedExercises);

        // Calculate and set estimated duration
        workoutPlan.calculateEstimatedDuration();

        workoutPlan.setIsPublic(true);
        workoutPlan.setCreatedBy(1L); // System user
        workoutPlan.setCreatedAt(routine.getCreatedAt());
        workoutPlan.setUpdatedAt(routine.getUpdatedAt());
        workoutPlan.setUsageCount(0L);

        return workoutPlan;
    }

    private PlannedExercise convertToPlannedExercise(HevyWorkoutRoutine.HevyExercise exercise) {
        PlannedExercise plannedExercise = new PlannedExercise();
        plannedExercise.setIndex(exercise.getIndex());
        plannedExercise.setTitle(exercise.getTitle());
        plannedExercise.setNotes(exercise.getNotes());
        plannedExercise.setExerciseTemplateId(exercise.getExerciseTemplateId());
        plannedExercise.setSupersetId(exercise.getSupersetId());
        plannedExercise.setRestSeconds(exercise.getRestSeconds());

        // Convert sets
        List<PlannedExercise.PlannedSet> sets = exercise.getSets().stream()
                .map(this::convertToPlannedSet)
                .collect(Collectors.toList());
        plannedExercise.setSets(sets);

        return plannedExercise;
    }

    private PlannedExercise.PlannedSet convertToPlannedSet(HevyWorkoutRoutine.HevySet set) {
        PlannedExercise.PlannedSet plannedSet = new PlannedExercise.PlannedSet();
        plannedSet.setIndex(set.getIndex());
        plannedSet.setType(set.getType());
        plannedSet.setWeightKg(set.getWeightKg());
        plannedSet.setReps(set.getReps());
        plannedSet.setDistanceMeters(set.getDistanceMeters());
        plannedSet.setDurationSeconds(set.getDurationSeconds());
        plannedSet.setCustomMetric(set.getCustomMetric());
        return plannedSet;
    }
}