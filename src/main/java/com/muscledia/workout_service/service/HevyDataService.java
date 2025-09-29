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

import java.util.*;
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

    /**
     * FIXED: Ensures routine folders are fetched and saved BEFORE routines
     * This prevents orphaned workout plans with missing folder references
     */
    public Mono<Void> fetchAndPopulateAllData() {
        log.info("Starting comprehensive data fetch from Hevy API");

        return fetchAndPopulateRoutineFolders()
                .doOnSuccess(v -> log.info("Routine folders fetch completed"))
                .then(fetchAndPopulateRoutines())
                .doOnSuccess(v -> log.info("Routines fetch completed"))
                .then(validateDataIntegrity())
                .doOnSuccess(v -> log.info("Data integrity validation completed"))
                .then();
    }

    /**
     * FIXED: Fetch routines and immediately associate them with folders
     */
    public Mono<Void> fetchAndPopulateRoutinesWithFolderAssociation() {
        log.info("Starting to fetch routines from {} pages with immediate folder association", maxRoutinesPages);

        return getExistingFolderIdsWithMapping()
                .flatMap(folderMapping -> {
                    log.info("Found {} existing routine folders for association", folderMapping.size());

                    return Flux.range(1, maxRoutinesPages)
                            .flatMap(page -> fetchRoutinesPage(page)
                                    .onErrorResume(error -> {
                                        log.error("Failed to fetch routines page {}: {}", page, error.getMessage());
                                        return Mono.empty();
                                    }))
                            .doOnNext(response -> log.info("Fetched {} routines from page {}",
                                    response.getRoutines().size(), response.getPage()))
                            .flatMapIterable(HevyApiResponse::getRoutines)
                            .filter(routine -> validateRoutineFolder(routine, folderMapping.keySet()))
                            .map(routine -> convertToWorkoutPlan(routine, folderMapping))
                            // Process immediately to maintain association
                            .flatMap(this::saveWorkoutPlanAndUpdateFolder, 5) // Limit concurrency
                            .then();
                })
                .doOnSuccess(v -> log.info("Completed fetching routines with folder relationships"));
    }

    /**
     * FIXED: Get existing folder IDs with their MongoDB _id mapping
     */
    private Mono<Map<String, String>> getExistingFolderIdsWithMapping() {
        return routineFolderRepository.findAll()
                .collectMap(
                        folder -> String.valueOf(folder.getHevyId()), // Hevy API folder ID
                        RoutineFolder::getId // MongoDB _id
                )
                .doOnNext(mapping -> log.debug("Folder ID mapping: {} entries", mapping.size()));
    }

    /**
     * FIXED: Enhanced workout plan conversion with proper folder ID resolution
     */
    private WorkoutPlan convertToWorkoutPlan(HevyWorkoutRoutine routine, Map<String, String> folderMapping) {
        WorkoutPlan workoutPlan = new WorkoutPlan();
        workoutPlan.setId(routine.getId());
        workoutPlan.setTitle(routine.getTitle());
        workoutPlan.setDescription("Imported from Hevy");

        // CRITICAL: Map Hevy folder ID to MongoDB folder ID
        String hevyFolderId = String.valueOf(routine.getFolderId());
        String mongoFolderId = folderMapping.get(hevyFolderId);

        if (mongoFolderId == null) {
            log.warn("No MongoDB folder found for Hevy folder ID: {} (routine: {})",
                    hevyFolderId, routine.getTitle());
            throw new RuntimeException("Folder mapping not found for routine: " + routine.getTitle());
        }

        workoutPlan.setFolderId(mongoFolderId);
        log.debug("Mapped routine '{}' to folder: Hevy ID {} -> MongoDB ID {}",
                routine.getTitle(), hevyFolderId, mongoFolderId);

        // Convert exercises
        List<PlannedExercise> plannedExercises = routine.getExercises().stream()
                .map(exercise -> convertToPlannedExercise(exercise, mongoFolderId))
                .collect(Collectors.toList());
        workoutPlan.setExercises(plannedExercises);

        // Set other properties
        workoutPlan.calculateEstimatedDuration();
        workoutPlan.setIsPublic(true);
        workoutPlan.setCreatedBy(null);
        workoutPlan.setCreatedAt(routine.getCreatedAt());
        workoutPlan.setUpdatedAt(routine.getUpdatedAt());
        workoutPlan.setUsageCount(0L);

        return workoutPlan;
    }

    /**
     * FIXED: Save workout plan and immediately update the parent folder
     */
    private Mono<WorkoutPlan> saveWorkoutPlanAndUpdateFolder(WorkoutPlan workoutPlan) {
        log.debug("Saving workout plan '{}' and updating folder '{}'",
                workoutPlan.getTitle(), workoutPlan.getFolderId());

        return workoutPlanRepository.save(workoutPlan)
                .flatMap(savedPlan ->
                        addWorkoutPlanToFolderAtomic(savedPlan.getFolderId(), savedPlan.getId())
                                .thenReturn(savedPlan)
                )
                .doOnSuccess(saved -> log.debug("Successfully saved and associated: '{}'", saved.getTitle()))
                .onErrorResume(error -> {
                    log.error("Failed to save/associate workout plan '{}': {}",
                            workoutPlan.getTitle(), error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Atomic operation to add workout plan ID to folder
     */
    private Mono<RoutineFolder> addWorkoutPlanToFolderAtomic(String folderId, String workoutPlanId) {
        return routineFolderRepository.findById(folderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Folder not found: " + folderId)))
                .flatMap(folder -> {
                    // Ensure workoutPlanIds list is initialized
                    if (folder.getWorkoutPlanIds() == null) {
                        folder.setWorkoutPlanIds(new ArrayList<>());
                    }

                    // Add if not already present
                    if (!folder.getWorkoutPlanIds().contains(workoutPlanId)) {
                        folder.addWorkoutPlanId(workoutPlanId);

                        return routineFolderRepository.save(folder)
                                .doOnSuccess(updated -> log.debug(
                                        "Added workout plan {} to folder '{}' (total: {})",
                                        workoutPlanId, updated.getTitle(), updated.getWorkoutPlanCount()));
                    } else {
                        log.debug("Workout plan {} already in folder '{}'", workoutPlanId, folder.getTitle());
                        return Mono.just(folder);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to add workout plan {} to folder {}: {}",
                            workoutPlanId, folderId, error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * ENHANCED: Validate and fix any missing folder associations
     */
    private Mono<Void> validateAndFixFolderAssociations() {
        log.info("🔧 Starting validation and fix of folder associations");

        return workoutPlanRepository.findAll()
                .groupBy(WorkoutPlan::getFolderId)
                .flatMap(group ->
                        group.collectList()
                                .flatMap(plans -> {
                                    String folderId = group.key();
                                    List<String> planIds = plans.stream()
                                            .map(WorkoutPlan::getId)
                                            .collect(Collectors.toList());

                                    log.debug("Validating folder {} with {} workout plans", folderId, planIds.size());

                                    return routineFolderRepository.findById(folderId)
                                            .flatMap(folder -> {
                                                // Check if folder has all expected workout plan IDs
                                                Set<String> existingIds = new HashSet<>(
                                                        folder.getWorkoutPlanIds() != null ?
                                                                folder.getWorkoutPlanIds() : Collections.emptyList()
                                                );

                                                Set<String> expectedIds = new HashSet<>(planIds);

                                                if (!existingIds.equals(expectedIds)) {
                                                    log.info("🔧 Fixing associations for folder '{}': {} -> {} plans",
                                                            folder.getTitle(), existingIds.size(), expectedIds.size());

                                                    folder.setWorkoutPlanIds(planIds);
                                                    return routineFolderRepository.save(folder)
                                                            .doOnSuccess(saved -> log.info(
                                                                    "✅ Fixed folder '{}': now has {} workout plans",
                                                                    saved.getTitle(), saved.getWorkoutPlanCount()));
                                                } else {
                                                    log.debug("✅ Folder '{}' already has correct associations", folder.getTitle());
                                                    return Mono.just(folder);
                                                }
                                            })
                                            .then();
                                })
                )
                .then()
                .doOnSuccess(v -> log.info("🔧 Folder association validation and fix completed"));
    }

    /**
     * ENHANCED: Get comprehensive statistics including association status
     */
    public Mono<Map<String, Object>> getDataStatisticsWithAssociations() {
        return Mono.zip(
                        routineFolderRepository.count(),
                        workoutPlanRepository.count(),
                        findOrphanedWorkoutPlans().count(),
                        getFoldersWithoutWorkoutPlans(),
                        getAverageWorkoutPlansPerFolder()
                )
                .map(results -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalFolders", results.getT1());
                    stats.put("totalWorkoutPlans", results.getT2());
                    stats.put("orphanedPlans", results.getT3());
                    stats.put("emptyFolders", results.getT4());
                    stats.put("avgPlansPerFolder", results.getT5());
                    stats.put("dataIntegrityScore",
                            results.getT2() > 0 ? (double)(results.getT2() - results.getT3()) / results.getT2() * 100 : 100.0);
                    stats.put("associationCompleteness",
                            results.getT1() > 0 ? (double)(results.getT1() - results.getT4()) / results.getT1() * 100 : 100.0);
                    return stats;
                });
    }

    /**
     * FIXED: Find folders that have no associated workout plans
     */
    private Mono<Long> getFoldersWithoutWorkoutPlans() {
        return routineFolderRepository.findAll()
                .filter(folder -> folder.getWorkoutPlanIds() == null || folder.getWorkoutPlanIds().isEmpty())
                .count();
    }

    /**
     * Calculate average workout plans per folder
     */
    private Mono<Double> getAverageWorkoutPlansPerFolder() {
        return routineFolderRepository.findAll()
                .map(folder -> folder.getWorkoutPlanIds() != null ? folder.getWorkoutPlanIds().size() : 0)
                .collect(Collectors.averagingInt(Integer::intValue));
    }

    /**
     * FIXED: Get detailed folder statistics with correct mapping
     */
    public Flux<Map<String, Object>> getFoldersWithDetailedStats() {
        return routineFolderRepository.findAll()
                .flatMap(folder -> {
                    String hevyIdAsString = String.valueOf(folder.getHevyId());

                    // Get actual workout plan count from database using hevyId
                    return workoutPlanRepository.findByFolderId(hevyIdAsString)
                            .count()
                            .map(actualCount -> {
                                Map<String, Object> folderStats = new HashMap<>();
                                folderStats.put("id", folder.getId());
                                folderStats.put("hevyId", folder.getHevyId());
                                folderStats.put("title", folder.getTitle());
                                folderStats.put("expectedCount", folder.getWorkoutPlanCount());
                                folderStats.put("actualCount", actualCount);
                                folderStats.put("workoutPlanIds", folder.getWorkoutPlanIds());
                                folderStats.put("searchedFolderId", hevyIdAsString); // Show what we're searching for
                                folderStats.put("needsSync", !Objects.equals(
                                        (long) folder.getWorkoutPlanCount(), actualCount));
                                return folderStats;
                            });
                });
    }

    /**
     * Clean and reimport all data (nuclear option)
     */
    public Mono<String> cleanAndReimportAllData() {
        log.warn("🚨 Starting CLEAN REIMPORT - deleting all existing data");

        return Mono.fromRunnable(() -> log.info("🗑️ Deleting all workout plans..."))
                .then(workoutPlanRepository.deleteAll())
                .then(Mono.fromRunnable(() -> log.info("🗑️ Deleting all routine folders...")))
                .then(routineFolderRepository.deleteAll())
                .then(Mono.fromRunnable(() -> log.info("📥 Starting fresh import...")))
                .then(fetchAndPopulateAllData())
                .then(getDataStatisticsWithAssociations())
                .map(stats -> String.format(
                        "Clean reimport completed - Folders: %d, Plans: %d, Integrity: %.1f%%, Associations: %.1f%%",
                        (Long) stats.get("totalFolders"),
                        (Long) stats.get("totalWorkoutPlans"),
                        (Double) stats.get("dataIntegrityScore"),
                        (Double) stats.get("associationCompleteness")
                ))
                .doOnSuccess(result -> log.info("✅ Clean reimport completed: {}", result));
    }

    /**
     * Enhanced validation with detailed reporting
     */
    public Mono<Map<String, Object>> validateDataIntegrityDetailed() {
        log.info("🔍 Starting detailed data integrity validation");

        return Mono.zip(
                // Get all folders
                routineFolderRepository.findAll().collectList(),
                // Get all workout plans
                workoutPlanRepository.findAll().collectList()
        ).flatMap(data -> {
            List<RoutineFolder> folders = data.getT1();
            List<WorkoutPlan> workoutPlans = data.getT2();

            Map<String, Object> report = new HashMap<>();

            // Group workout plans by folder
            Map<String, List<WorkoutPlan>> plansByFolder = workoutPlans.stream()
                    .collect(Collectors.groupingBy(WorkoutPlan::getFolderId));

            // Analyze each folder
            List<Map<String, Object>> folderAnalysis = folders.stream()
                    .map(folder -> {
                        Map<String, Object> analysis = new HashMap<>();
                        analysis.put("folderId", folder.getId());
                        analysis.put("folderTitle", folder.getTitle());
                        analysis.put("hevyId", folder.getHevyId());

                        List<WorkoutPlan> actualPlans = plansByFolder.getOrDefault(folder.getId(), Collections.emptyList());
                        List<String> expectedPlanIds = folder.getWorkoutPlanIds() != null ?
                                folder.getWorkoutPlanIds() : Collections.emptyList();

                        analysis.put("expectedPlanCount", expectedPlanIds.size());
                        analysis.put("actualPlanCount", actualPlans.size());
                        analysis.put("planTitles", actualPlans.stream()
                                .map(WorkoutPlan::getTitle)
                                .collect(Collectors.toList()));
                        analysis.put("isConsistent", expectedPlanIds.size() == actualPlans.size());

                        return analysis;
                    })
                    .collect(Collectors.toList());

            // Find orphaned plans
            Set<String> validFolderIds = folders.stream()
                    .map(RoutineFolder::getId)
                    .collect(Collectors.toSet());

            List<Map<String, Object>> orphanedPlans = workoutPlans.stream()
                    .filter(plan -> !validFolderIds.contains(plan.getFolderId()))
                    .map(plan -> {
                        Map<String, Object> orphanInfo = new HashMap<>(); // FIXED: Create mutable map
                        orphanInfo.put("planId", plan.getId());
                        orphanInfo.put("planTitle", plan.getTitle());
                        orphanInfo.put("invalidFolderId", plan.getFolderId());
                        return orphanInfo;
                    })
                    .collect(Collectors.toList());

            // Compile report
            report.put("totalFolders", folders.size());
            report.put("totalWorkoutPlans", workoutPlans.size());
            report.put("orphanedPlans", orphanedPlans);
            report.put("orphanedPlanCount", orphanedPlans.size());
            report.put("folderAnalysis", folderAnalysis);
            report.put("inconsistentFolders", folderAnalysis.stream()
                    .filter(analysis -> !(Boolean) analysis.get("isConsistent"))
                    .count());

            return Mono.just(report);
        });
    }

    /**
            * FIXED: Force sync all folder associations using correct mapping
    */
    public Mono<Map<String, Object>> forceSyncAllFolderAssociations() {
        log.info("🔄 Starting FORCE sync of all folder associations");

        return routineFolderRepository.findAll()
                .flatMap(folder -> {
                    String hevyIdAsString = String.valueOf(folder.getHevyId());
                    log.debug("Force syncing folder '{}' - looking for workout plans with folderId: {}",
                            folder.getTitle(), hevyIdAsString);

                    // CRITICAL FIX: Search by hevyId, not MongoDB _id
                    return workoutPlanRepository.findByFolderId(hevyIdAsString)
                            .map(WorkoutPlan::getId)
                            .collectList()
                            .flatMap(planIds -> {
                                if (!planIds.isEmpty()) {
                                    folder.setWorkoutPlanIds(planIds);
                                    return routineFolderRepository.save(folder)
                                            .doOnSuccess(saved -> log.info(
                                                    "🔄 Force synced folder '{}' (hevyId: {}) with {} plans",
                                                    saved.getTitle(), saved.getHevyId(), saved.getWorkoutPlanCount()));
                                } else {
                                    log.debug("No workout plans found for folder '{}' (hevyId: {})",
                                            folder.getTitle(), folder.getHevyId());
                                    return Mono.just(folder);
                                }
                            });
                })
                .count()
                .flatMap(syncedCount -> getDataStatisticsWithAssociations()
                        .map(stats -> {
                            stats.put("forceSyncedFolders", syncedCount);
                            stats.put("operationType", "FORCE_SYNC");
                            return stats;
                        }))
                .doOnSuccess(result -> log.info("🔄 Force sync completed: {}", result));
    }

    /**
     * ENHANCED: Batch processing method for better performance
     */
    public Mono<Void> fetchAndPopulateRoutines() {
        log.info("Starting to fetch routines from {} pages", maxRoutinesPages);

        return getExistingFolderIds()
                .flatMap(existingFolderIds -> {
                    log.info("Found {} existing routine folders", existingFolderIds.size());

                    return Flux.range(1, maxRoutinesPages)
                            .flatMap(page -> fetchRoutinesPage(page)
                                    .onErrorResume(error -> {
                                        log.error("Failed to fetch routines page {}: {}", page, error.getMessage());
                                        return Mono.empty();
                                    }))
                            .doOnNext(response -> log.info("Fetched {} routines from page {}",
                                    response.getRoutines().size(), response.getPage()))
                            .flatMapIterable(HevyApiResponse::getRoutines)
                            .filter(routine -> validateRoutineFolder(routine, existingFolderIds))
                            .map(this::convertToWorkoutPlan)
                            // Collect into batches for better performance
                            .buffer(10) // Process 10 workout plans at a time
                            .flatMap(this::processBatchOfWorkoutPlans)
                            .then();
                })
                .doOnSuccess(v -> log.info("Completed fetching routines with folder relationships"));
    }

    /**
     * Process a batch of workout plans and update folder relationships
     */
    private Mono<Void> processBatchOfWorkoutPlans(List<WorkoutPlan> workoutPlans) {
        log.debug("Processing batch of {} workout plans", workoutPlans.size());

        return Flux.fromIterable(workoutPlans)
                .flatMap(plan -> workoutPlanRepository.save(plan)
                        .doOnNext(saved -> log.debug("Saved: {}", saved.getTitle()))
                        .onErrorResume(error -> {
                            log.error("Failed to save workout plan '{}': {}", plan.getTitle(), error.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .flatMap(savedPlans -> {
                    // Group plans by folder for efficient updates
                    Map<String, List<WorkoutPlan>> plansByFolder = savedPlans.stream()
                            .collect(Collectors.groupingBy(WorkoutPlan::getFolderId));

                    return Flux.fromIterable(plansByFolder.entrySet())
                            .flatMap(entry -> updateFolderWithWorkoutPlans(entry.getKey(), entry.getValue()))
                            .then();
                });
    }

    /**
     * Update a folder with multiple workout plan IDs at once
     */
    private Mono<RoutineFolder> updateFolderWithWorkoutPlans(String folderId, List<WorkoutPlan> workoutPlans) {
        return routineFolderRepository.findById(folderId)
                .flatMap(folder -> {
                    // Add all workout plan IDs to the folder
                    workoutPlans.forEach(plan -> folder.addWorkoutPlanId(plan.getId()));

                    return routineFolderRepository.save(folder)
                            .doOnSuccess(saved -> log.info("Updated folder '{}' with {} workout plans (total: {})",
                                    saved.getTitle(), workoutPlans.size(), saved.getWorkoutPlanCount()));
                })
                .onErrorResume(error -> {
                    log.error("Failed to update folder {} with workout plans: {}", folderId, error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> fetchAndPopulateRoutineFolders() {
        log.info("Starting to fetch routine folders from {} pages", maxRoutineFoldersPages);
        return Flux.range(1, maxRoutineFoldersPages)
                .flatMap(page -> fetchRoutineFoldersPage(page)
                        .onErrorResume(error -> {
                            log.error("Failed to fetch routine folders page {}: {}", page, error.getMessage());
                            return Mono.empty(); // Continue with other pages
                        }))
                .doOnNext(response -> log.info("Fetched {} folders from page {}",
                        response.getFolders().size(), response.getPage()))
                .flatMapIterable(HevyRoutineFolderResponse::getFolders)
                .map(this::convertToRoutineFolder)
                .flatMap(folder -> routineFolderRepository.save(folder)
                        .doOnSuccess(saved -> log.debug("Saved routine folder: {} (ID: {})",
                                saved.getTitle(), saved.getHevyId()))
                        .onErrorResume(error -> {
                            log.error("Failed to save routine folder '{}': {}",
                                    folder.getTitle(), error.getMessage());
                            return Mono.empty(); // Continue with other folders
                        }))
                .then()
                .doOnSuccess(v -> log.info("Completed fetching routine folders"));
    }

    /**
     * Get all existing folder IDs from the database
     */
    private Mono<Set<String>> getExistingFolderIds() {
        return routineFolderRepository.findAll()
                .map(folder -> String.valueOf(folder.getHevyId()))
                .collect(Collectors.toSet())
                .doOnNext(folderIds -> log.debug("Existing folder IDs: {}", folderIds));
    }

    /**
     * Validate that a routine has a corresponding folder
     */
    private boolean validateRoutineFolder(HevyWorkoutRoutine routine, Set<String> existingFolderIds) {
        String folderId = String.valueOf(routine.getFolderId());
        boolean isValid = existingFolderIds.contains(folderId);

        if (!isValid) {
            log.warn("Skipping routine '{}' - folder ID {} not found in existing folders",
                    routine.getTitle(), folderId);
        }

        return isValid;
    }


    /**
     * ENHANCED: Comprehensive data fetch with automatic sync
     */
    public Mono<Map<String, Object>> fetchAndPopulateAllDataWithSync() {
        log.info("Starting comprehensive data fetch with auto-sync");

        return fetchAndPopulateAllData()
                .then(syncFolderWorkoutPlanIds())
                .then(getDataStatistics())
                .flatMap(stats -> {
                    Map<String, Object> result = new HashMap<>(stats);

                    // Add sync information
                    return getFolderSyncStatus()
                            .map(syncStatus -> {
                                result.put("syncedFolders", syncStatus.get("foldersWithWorkoutPlanIds"));
                                result.put("autoSyncCompleted", true);
                                return result;
                            });
                })
                .doOnSuccess(result -> log.info("Comprehensive fetch with auto-sync completed: {}", result));
    }

    /**
     * FIXED: Add missing repository method or update existing sync logic
     */
    public Mono<Long> syncFolderWorkoutPlanIds() {
        log.info("Starting sync of workout_plan_ids for existing folders");

        return routineFolderRepository.findAll()
                .filter(folder -> folder.getWorkoutPlanIds() == null || folder.getWorkoutPlanIds().isEmpty())
                .flatMap(folder -> {
                    log.debug("Syncing folder: '{}' (MongoDB ID: {}, Hevy ID: {})",
                            folder.getTitle(), folder.getId(), folder.getHevyId());

                    // CRITICAL FIX: Search by hevyId, not MongoDB _id
                    String hevyIdAsString = String.valueOf(folder.getHevyId());

                    return workoutPlanRepository.findByFolderId(hevyIdAsString) // Use hevyId
                            .map(WorkoutPlan::getId)
                            .collectList()
                            .flatMap(planIds -> {
                                if (!planIds.isEmpty()) {
                                    folder.setWorkoutPlanIds(planIds);
                                    return routineFolderRepository.save(folder)
                                            .doOnSuccess(saved -> log.info("Synced folder '{}' (hevyId: {}) with {} workout plans",
                                                    saved.getTitle(), saved.getHevyId(), saved.getWorkoutPlanIds().size()))
                                            .thenReturn(1L); // Count this as synced
                                } else {
                                    log.debug("No workout plans found for folder: '{}' (hevyId: {})",
                                            folder.getTitle(), folder.getHevyId());
                                    return Mono.just(0L); // Count as not synced
                                }
                            });
                })
                .reduce(0L, Long::sum)
                .doOnSuccess(count -> log.info("Synced workout_plan_ids for {} folders", count))
                .onErrorResume(e -> {
                    log.error("Error syncing workout_plan_ids", e);
                    return Mono.just(0L);
                });
    }

    /**
     * Get folder sync status (moved from controller)
     */
    public Mono<Map<String, Object>> getFolderSyncStatus() {
        log.debug("Checking folder sync status");

        return Mono.zip(
                        routineFolderRepository.count(),
                        routineFolderRepository.findAll()
                                .filter(folder -> folder.getWorkoutPlanIds() != null && !folder.getWorkoutPlanIds().isEmpty())
                                .count(),
                        workoutPlanRepository.count()
                )
                .map(counts -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("totalFolders", counts.getT1());
                    status.put("foldersWithWorkoutPlanIds", counts.getT2());
                    status.put("foldersNeedingSync", counts.getT1() - counts.getT2());
                    status.put("totalWorkoutPlans", counts.getT3());
                    status.put("syncNeeded", (counts.getT1() - counts.getT2()) > 0);

                    return status;
                });
    }

    /**
     * FIXED: Enhanced page fetching with retry logic and proper error handling
     */
    private Mono<HevyApiResponse> fetchRoutinesPage(int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{version}/routines")
                        .queryParam("page", page)
                        .queryParam("pageSize", routinesPageSize)
                        .build(apiVersion))
                .retrieve()
                .bodyToMono(HevyApiResponse.class)
                .doOnNext(response -> {
                    log.debug("Routines Page {}: {} routines, {} total pages",
                            page, response.getRoutines() != null ? response.getRoutines().size() : 0,
                            response.getPageCount());

                    // Additional validation
                    if (response.getRoutines() == null) {
                        log.warn("No routines found in response for page {}", page);
                    }
                })
                .retry(3) // Retry up to 3 times on failure
                .onErrorMap(ex -> new RuntimeException("Failed to fetch routines page " + page + ": " + ex.getMessage(), ex));
    }

    /**
     * FIXED: Enhanced folder page fetching with retry logic and proper error handling
     */
    private Mono<HevyRoutineFolderResponse> fetchRoutineFoldersPage(int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{version}/routine_folders")
                        .queryParam("page", page)
                        .queryParam("pageSize", routineFoldersPageSize)
                        .build(apiVersion))
                .retrieve()
                .bodyToMono(HevyRoutineFolderResponse.class)
                .doOnNext(response -> {
                    log.debug("Folders Page {}: {} folders, {} total pages",
                            page, response.getFolders() != null ? response.getFolders().size() : 0,
                            response.getPageCount());

                    // Additional validation
                    if (response.getFolders() == null) {
                        log.warn("No folders found in response for page {}", page);
                    }
                })
                .retry(3) // Retry up to 3 times on failure
                .onErrorMap(ex -> new RuntimeException("Failed to fetch routine folders page " + page + ": " + ex.getMessage(), ex));
    }


    /**
     * FIXED: Enhanced routine folder conversion
     */
    private RoutineFolder convertToRoutineFolder(HevyRoutineFolder hevyFolder) {
        RoutineFolder folder = new RoutineFolder();
        folder.setHevyId(hevyFolder.getId());
        folder.setTitle(hevyFolder.getTitle());
        folder.setFolderIndex(hevyFolder.getFolderIndex());
        folder.setCreatedAt(hevyFolder.getCreatedAt());
        folder.setUpdatedAt(hevyFolder.getUpdatedAt());
        folder.setIsPublic(true); // Hevy folders are public
        folder.setCreatedBy(null); // System user
        folder.setUsageCount(0L);
        folder.setWorkoutPlanIds(new ArrayList<>()); // Initialize empty list

        // Parse metadata from title
        folder.parseMetadataFromTitle();

        return folder;
    }

    /**
     * FIXED: Enhanced workout plan conversion - Store hevyId as folderId, not MongoDB _id
     */
    private WorkoutPlan convertToWorkoutPlan(HevyWorkoutRoutine routine) {
        WorkoutPlan workoutPlan = new WorkoutPlan();
        workoutPlan.setId(routine.getId());
        workoutPlan.setTitle(routine.getTitle());
        workoutPlan.setDescription("Imported from Hevy");

        // CRITICAL FIX: Store Hevy folder ID as folderId (not MongoDB _id)
        // This matches what we actually have in the database
        String hevyFolderId = String.valueOf(routine.getFolderId());
        workoutPlan.setFolderId(hevyFolderId); // Store as "1371024", not "688ec6292788864ae7958a98"

        // Convert exercises
        List<PlannedExercise> plannedExercises = routine.getExercises().stream()
                .map(exercise -> convertToPlannedExercise(exercise, hevyFolderId))
                .collect(Collectors.toList());
        workoutPlan.setExercises(plannedExercises);

        // Set other properties
        workoutPlan.calculateEstimatedDuration();
        workoutPlan.setIsPublic(true);
        workoutPlan.setCreatedBy(null);
        workoutPlan.setCreatedAt(routine.getCreatedAt());
        workoutPlan.setUpdatedAt(routine.getUpdatedAt());
        workoutPlan.setUsageCount(0L);

        return workoutPlan;
    }

    /**
     * FIXED: Updated HevyDataService methods to properly populate workout_plan_ids
     */
    public Mono<Void> populateWorkoutPlans(HevyApiResponse hevyApiResponse) {
        log.info("Manually populating {} workout plans", hevyApiResponse.getRoutines().size());

        return getExistingFolderIds()
                .flatMap(existingFolderIds ->
                        Flux.fromIterable(hevyApiResponse.getRoutines())
                                .filter(routine -> validateRoutineFolder(routine, existingFolderIds))
                                .map(this::convertToWorkoutPlan)
                                .flatMap(workoutPlan ->
                                        // Save the workout plan first
                                        workoutPlanRepository.save(workoutPlan)
                                                .doOnNext(saved -> log.debug("Saved workout plan: '{}'", saved.getTitle()))
                                                .flatMap(savedPlan ->
                                                        // Then update the folder's workout_plan_ids
                                                        addWorkoutPlanToFolder(savedPlan.getFolderId(), savedPlan.getId())
                                                                .thenReturn(savedPlan)
                                                )
                                )
                                .then()
                )
                .doOnSuccess(v -> log.info("Manual population completed with folder relationships updated"));
    }

    /**
     * ENHANCED: Method to add workout plan ID to folder's workout_plan_ids list
     */
    private Mono<RoutineFolder> addWorkoutPlanToFolder(String folderId, String workoutPlanId) {
        return routineFolderRepository.findById(folderId)
                .flatMap(folder -> {
                    folder.addWorkoutPlanId(workoutPlanId);
                    return routineFolderRepository.save(folder);
                })
                .doOnSuccess(updated -> log.debug("Added workout plan {} to folder '{}' (total: {})",
                        workoutPlanId, updated.getTitle(), updated.getWorkoutPlanCount()))
                .onErrorResume(error -> {
                    log.warn("Could not add workout plan {} to folder {}: {}",
                            workoutPlanId, folderId, error.getMessage());
                    return Mono.empty(); // Continue even if this fails
                });
    }

    /**
     * FIXED: Enhanced exercise conversion with folder_id inheritance
     */
    private PlannedExercise convertToPlannedExercise(HevyWorkoutRoutine.HevyExercise exercise, String folderId) {
        PlannedExercise plannedExercise = new PlannedExercise();
        plannedExercise.setIndex(exercise.getIndex());
        plannedExercise.setTitle(exercise.getTitle());
        plannedExercise.setNotes(exercise.getNotes());
        plannedExercise.setExerciseTemplateId(exercise.getExerciseTemplateId());
        plannedExercise.setSupersetId(exercise.getSupersetId());
        plannedExercise.setRestSeconds(exercise.getRestSeconds());


        // Convert sets with null safety
        List<PlannedExercise.PlannedSet> sets = exercise.getSets().stream()
                .filter(Objects::nonNull) // Filter out null sets
                .map(set -> convertToPlannedSet(set, folderId)) // Pass folder_id to sets too
                .collect(Collectors.toList());
        plannedExercise.setSets(sets);

        return plannedExercise;
    }

    /**
     * FIXED: Enhanced set conversion with folder_id inheritance
     */
    private PlannedExercise.PlannedSet convertToPlannedSet(HevyWorkoutRoutine.HevySet set, String folderId) {
        PlannedExercise.PlannedSet plannedSet = new PlannedExercise.PlannedSet();
        plannedSet.setIndex(set.getIndex());
        plannedSet.setType(set.getType());
        plannedSet.setWeightKg(set.getWeightKg());
        plannedSet.setReps(set.getReps());
        plannedSet.setDistanceMeters(set.getDistanceMeters());
        plannedSet.setDurationSeconds(set.getDurationSeconds());
        plannedSet.setCustomMetric(set.getCustomMetric());


        // FIXED: Handle rep ranges properly based on actual API structure
        if (set.getRepRange() != null) {
            plannedSet.setRepRangeStart(set.getRepRange().getStart());
            plannedSet.setRepRangeEnd(set.getRepRange().getEnd());
        }

        return plannedSet;
    }

    /**
     * Utility method to clean up orphaned workout plans
     */
    public Mono<Long> cleanupOrphanedWorkoutPlans() {
        log.info("🧹 Starting cleanup of orphaned workout plans");

        return findOrphanedWorkoutPlans()
                .flatMap(orphaned -> {
                    log.info("🗑️ Deleting orphaned workout plan: {} (Folder ID: {})",
                            orphaned.getTitle(), orphaned.getFolderId());
                    return workoutPlanRepository.delete(orphaned);
                })
                .count()
                .doOnNext(deletedCount -> log.info("🧹 Cleaned up {} orphaned workout plans", deletedCount));
    }

    /**
     * Get comprehensive statistics about the imported data
     */
    public Mono<Map<String, Object>> getDataStatistics() {
        return Mono.zip(
                        routineFolderRepository.count(),
                        workoutPlanRepository.count(),
                        findOrphanedWorkoutPlans().count()
                )
                .map(counts -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalFolders", counts.getT1());
                    stats.put("totalWorkoutPlans", counts.getT2());
                    stats.put("orphanedPlans", counts.getT3());
                    stats.put("dataIntegrityScore",
                            counts.getT2() > 0 ? (double)(counts.getT2() - counts.getT3()) / counts.getT2() * 100 : 100.0);
                    return stats;
                });
    }

    /**
     * Validate data integrity after import
     */
    private Mono<Void> validateDataIntegrity() {
        log.info("🔍 Starting data integrity validation");

        return Mono.zip(
                        routineFolderRepository.count(),
                        workoutPlanRepository.count()
                )
                .flatMap(counts -> {
                    Long folderCount = counts.getT1();
                    Long planCount = counts.getT2();

                    log.info("📊 Data Summary - Folders: {}, Workout Plans: {}", folderCount, planCount);

                    // Check for orphaned workout plans
                    return findOrphanedWorkoutPlans()
                            .collectList()
                            .doOnNext(orphaned -> {
                                if (!orphaned.isEmpty()) {
                                    log.warn("⚠️ Found {} orphaned workout plans:", orphaned.size());
                                    orphaned.forEach(plan ->
                                            log.warn("   - '{}' references missing folder ID: {}",
                                                    plan.getTitle(), plan.getFolderId()));
                                } else {
                                    log.info("✅ No orphaned workout plans found");
                                }
                            });
                })
                .then();
    }

    /**
     * Find workout plans that reference non-existent folders
     */
    private Flux<WorkoutPlan> findOrphanedWorkoutPlans() {
        return getExistingFolderIds()
                .flatMapMany(existingFolderIds ->
                        workoutPlanRepository.findAll()
                                .filter(plan -> !existingFolderIds.contains(plan.getFolderId()))
                );
    }
}