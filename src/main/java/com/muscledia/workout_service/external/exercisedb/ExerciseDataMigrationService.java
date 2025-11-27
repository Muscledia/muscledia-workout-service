package com.muscledia.workout_service.external.exercisedb;


import com.muscledia.workout_service.external.exercisedb.dto.ExerciseApiDTO;
import com.muscledia.workout_service.model.Exercise;
import com.muscledia.workout_service.model.enums.ExerciseCategory;
import com.muscledia.workout_service.model.enums.ExerciseDifficulty;
import com.muscledia.workout_service.repository.ExerciseRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExerciseDataMigrationService {
    @Value("${rapidapi.key}")
    private String rapidApiKey;

    @Value("${rapidapi.host}")
    private String rapidApiHost;

    private final ExerciseRepository exerciseRepository;
    private WebClient webClient;

    private static final int FETCH_LIMIT = 5000; // Fetch all at once!

    public ExerciseDataMigrationService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl("https://" + rapidApiHost)
                .defaultHeader("X-RapidAPI-Key", rapidApiKey)
                .defaultHeader("X-RapidAPI-Host", rapidApiHost)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
                .build();

        log.info("ExerciseDataMigrationService initialized with API host: {}", rapidApiHost);
    }

    /**
     * One-time migration: Fetch all exercises from RapidAPI and save to MongoDB
     * Optimized to fetch in large batches
     */
    public void migrateAllExercises() {
        log.info("Starting exercise data migration from RapidAPI");

        AtomicInteger totalMigrated = new AtomicInteger(0);

        try {
            // Fetch all exercises in one go
            log.info("Fetching up to {} exercises from API...", FETCH_LIMIT);

            List<ExerciseApiDTO> exercises = fetchAllExercises();

            if (exercises == null || exercises.isEmpty()) {
                log.warn("No exercises received from API");
                return;
            }

            log.info("Received {} exercises from API. Starting migration...", exercises.size());

            // Transform and save to MongoDB
            Flux.fromIterable(exercises)
                    .map(this::transformToExercise)
                    .flatMap(exerciseRepository::save)
                    .doOnNext(saved -> {
                        int count = totalMigrated.incrementAndGet();
                        if (count % 100 == 0) {
                            log.info("Saved {} exercises so far...", count);
                        }
                    })
                    .doOnError(error -> log.error("❌ Error saving exercise: {}", error.getMessage()))
                    .blockLast(); // Wait for all saves to complete

            log.info("Migration completed successfully! Total exercises migrated: {}", totalMigrated.get());

        } catch (Exception e) {
            log.error("Critical error during migration", e);
            throw new RuntimeException("Exercise migration failed", e);
        }
    }

    /**
     * Fetch all exercises from the API in one request
     */
    private List<ExerciseApiDTO> fetchAllExercises() {
        try {
            List<ExerciseApiDTO> exercises = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/exercises")
                            .queryParam("limit", FETCH_LIMIT)
                            .queryParam("offset", 0)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<ExerciseApiDTO>>() {})
                    .doOnSuccess(list -> {
                        if (list != null) {
                            log.info("Successfully fetched {} exercises from API", list.size());
                        }
                    })
                    .doOnError(error -> log.error("Failed to fetch exercises: {}", error.getMessage()))
                    .block();

            return exercises;

        } catch (Exception e) {
            log.error("Exception fetching exercises: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Transform API DTO to Exercise entity
     */
    private Exercise transformToExercise(ExerciseApiDTO dto) {
        return Exercise.builder()
                .externalId(dto.getId())
                .name(dto.getName())
                .bodyPart(dto.getBodyPart())
                .equipment(dto.getEquipment())
                .targetMuscle(dto.getTarget())
                .secondaryMuscles(dto.getSecondaryMuscles())
                .instructions(dto.getInstructions())
                .description(dto.getDescription())
                .difficulty(mapDifficulty(dto.getDifficulty()))
                .category(mapCategory(dto.getCategory()))
                .keywords(dto.getKeywords())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isActive(true)
                .usageCount(0)
                .build();
    }

    private ExerciseDifficulty mapDifficulty(String difficulty) {
        if (difficulty == null) return ExerciseDifficulty.INTERMEDIATE;

        return switch (difficulty.toLowerCase()) {
            case "beginner" -> ExerciseDifficulty.BEGINNER;
            case "intermediate" -> ExerciseDifficulty.INTERMEDIATE;
            case "advanced" -> ExerciseDifficulty.ADVANCED;
            default -> {
                log.debug("Unknown difficulty '{}', defaulting to INTERMEDIATE", difficulty);
                yield ExerciseDifficulty.INTERMEDIATE;
            }
        };
    }

    private ExerciseCategory mapCategory(String category) {
        if (category == null) return ExerciseCategory.STRENGTH;

        try {
            return ExerciseCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("Unknown category '{}', defaulting to STRENGTH", category);
            return ExerciseCategory.STRENGTH;
        }
    }
}
