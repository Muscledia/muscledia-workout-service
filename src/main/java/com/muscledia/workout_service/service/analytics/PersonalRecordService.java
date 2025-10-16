package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.event.PersonalRecordEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.repository.analytics.PersonalRecordRepository;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;
    private final ReactiveMongoTemplate reactiveMongoTemplate;
    private final TransactionalEventPublisher eventPublisher;



    /**
     * Process a workout and detect any new personal records with event publishing
     */
    public Mono<List<PersonalRecord>> processWorkoutForPRs(Workout workout) {
        log.info("Processing workout {} for PRs for user {}", workout.getId(), workout.getUserId());

        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> checkForPRsWithEvents(workout.getUserId(), exercise, workout))
                .collectList()
                .doOnSuccess(prs -> log.info("Found {} new PRs for workout {}", prs.size(), workout.getId()));
    }

    /**
     * Check for personal records in a single exercise with event publishing
     */
    private Flux<PersonalRecord> checkForPRsWithEvents(Long userId, WorkoutExercise exercise, Workout workout) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return Flux.empty();
        }

        // Use whatever exercise name is available, fallback to exercise ID if name is null/empty
        String exerciseName = getDisplayExerciseName(exercise);

        return processExerciseSetsForPRs(userId, exercise.getExerciseId(), exerciseName, exercise.getSets(), workout.getId());
    }

    /**
     * Get a displayable exercise name, handling cases where name might be null or generic
     */
    private String getDisplayExerciseName(WorkoutExercise exercise) {
        if (exercise.getExerciseName() != null && !exercise.getExerciseName().trim().isEmpty()) {
            return exercise.getExerciseName();
        }
        // Fallback to exercise ID if no name available
        return "Exercise " + exercise.getExerciseId();
    }

    /**
     * Process exercise sets for PRs and publish events
     */
    private Flux<PersonalRecord> processExerciseSetsForPRs(Long userId, String exerciseId,
                                                           String exerciseName, List<WorkoutSet> sets, String workoutId) {
        return Flux.fromIterable(sets)
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .flatMap(set -> checkSetForPRsWithEvents(userId, exerciseId, exerciseName, set))
                .distinct() // Avoid duplicate PRs
                .flatMap(personalRecord -> {
                    // Publish PR event for each new personal record using the existing event system
                    return publishPersonalRecordEvent(personalRecord, workoutId)
                            .thenReturn(personalRecord)
                            .onErrorResume(error -> {
                                log.error("Failed to publish PR event for user {}: {}", userId, error.getMessage());
                                // Don't fail the PR creation if event publishing fails
                                return Mono.just(personalRecord);
                            });
                });
    }

    /**
     * Publish PersonalRecordEvent using the existing TransactionalEventPublisher
     */
    private Mono<Void> publishPersonalRecordEvent(PersonalRecord personalRecord, String workoutId) {
        try {
            PersonalRecordEvent event = PersonalRecordEvent.builder()
                    .userId(personalRecord.getUserId())
                    .exerciseName(personalRecord.getExerciseName())
                    .exerciseId(personalRecord.getExerciseId())
                    .recordType(personalRecord.getRecordType())
                    .newValue(personalRecord.getValue())
                    .previousValue(personalRecord.getPreviousRecord())
                    .unit(determineUnit(personalRecord.getRecordType()))
                    .workoutId(workoutId)
                    .reps(personalRecord.getReps())
                    .weight(personalRecord.getWeight())
                    .achievedAt(personalRecord.getAchievedDate() != null ?
                            personalRecord.getAchievedDate().atZone(java.time.ZoneOffset.UTC).toInstant() :
                            Instant.now())
                    .improvementPercentage(personalRecord.getImprovementPercentage())
                    .build();

            log.info("Publishing PR event for user {} - {} {} {}",
                    personalRecord.getUserId(), personalRecord.getValue(),
                    determineUnit(personalRecord.getRecordType()),
                    personalRecord.getExerciseName());

            return eventPublisher.publishPersonalRecord(event);
        } catch (Exception e) {
            log.error("Error creating PersonalRecordEvent: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * ENHANCED: Check and update personal records with comprehensive retry logic and exercise name
     */
    public Mono<List<PersonalRecord>> checkAndUpdatePersonalRecordsWithRetry(Long userId, String exerciseId,
                                                                             String exerciseName, List<WorkoutSet> sets) {
        return checkAndUpdatePersonalRecords(userId, exerciseId, exerciseName, sets)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .filter(throwable -> throwable instanceof DuplicateKeyException)
                        .doBeforeRetry(retrySignal ->
                                log.debug("Retrying personal record update, attempt: {}", retrySignal.totalRetries() + 1)))
                .onErrorResume(DuplicateKeyException.class, error -> {
                    log.warn("Failed to update personal records after retries: {}", error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * FIXED: Check and update personal records for an exercise's sets with exercise name
     */
    private Mono<List<PersonalRecord>> checkAndUpdatePersonalRecords(Long userId, String exerciseId,
                                                                     String exerciseName, List<WorkoutSet> sets) {
        log.debug("Checking PRs for user {} exercise '{}' ({}) with {} sets",
                userId, exerciseName, exerciseId, sets != null ? sets.size() : 0);

        if (sets == null || sets.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }

        return Flux.fromIterable(sets)
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .flatMap(set -> checkSetForPRsWithRetry(userId, exerciseId, exerciseName, set))
                .collectList()
                .doOnSuccess(prs -> log.debug("Found {} new PRs for exercise '{}'", prs.size(), exerciseName))
                .onErrorResume(error -> {
                    log.warn("Error checking PRs for user {} exercise '{}': {}", userId, exerciseName, error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Check for personal records in a single set with retry and event publishing
     */
    private Flux<PersonalRecord> checkSetForPRsWithEvents(Long userId, String exerciseId,
                                                          String exerciseName, WorkoutSet set) {
        return checkSetForPRs(userId, exerciseId, exerciseName, set)
                .onErrorResume(DuplicateKeyException.class, error -> {
                    log.debug("Duplicate key error when checking set PRs, retrying: {}", error.getMessage());
                    return Flux.empty(); // Skip this set's PRs if duplicate key error
                });
    }

    /**
     * Check for personal records in a single set with retry
     */
    private Flux<PersonalRecord> checkSetForPRsWithRetry(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        return checkSetForPRs(userId, exerciseId, exerciseName, set)
                .onErrorResume(DuplicateKeyException.class, error -> {
                    log.debug("Duplicate key error when checking set PRs, retrying: {}", error.getMessage());
                    return Flux.empty(); // Skip this set's PRs if duplicate key error
                });
    }

    /**
     * Check for personal records in a single set with exercise name
     */
    private Flux<PersonalRecord> checkSetForPRs(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        return Flux.concat(
                checkMaxWeightPRFromSet(userId, exerciseId, exerciseName, set),
                checkMaxVolumePRFromSet(userId, exerciseId, exerciseName, set),
                checkMaxRepsPRFromSet(userId, exerciseId, exerciseName, set),
                checkEstimated1RMPRFromSet(userId, exerciseId, exerciseName, set)
        ).filter(Objects::nonNull);
    }

    /**
     * FIXED: Check for maximum weight PR from a set using upsert with exercise name
     */
    private Mono<PersonalRecord> checkMaxWeightPRFromSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        BigDecimal currentWeight = set.getWeightKg();

        return getCurrentOrCreateEmptyPR(userId, exerciseId, "MAX_WEIGHT")
                .flatMap(existingPR -> {
                    BigDecimal existingWeight = existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO;

                    if (currentWeight.compareTo(existingWeight) > 0) {
                        return upsertPersonalRecord(userId, exerciseId, exerciseName, set, "MAX_WEIGHT",
                                currentWeight, existingWeight);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Check for maximum volume PR from a set using upsert with exercise name
     */
    private Mono<PersonalRecord> checkMaxVolumePRFromSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        BigDecimal currentVolume = set.getVolume(); // This is already calculated in WorkoutSet

        if (currentVolume == null || currentVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.empty();
        }

        return getCurrentOrCreateEmptyPR(userId, exerciseId, "MAX_VOLUME")
                .flatMap(existingPR -> {
                    BigDecimal existingVolume = existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO;

                    if (currentVolume.compareTo(existingVolume) > 0) {
                        return upsertPersonalRecord(userId, exerciseId, exerciseName, set, "MAX_VOLUME",
                                currentVolume, existingVolume);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Check for maximum reps PR from a set using upsert with exercise name
     */
    private Mono<PersonalRecord> checkMaxRepsPRFromSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        BigDecimal setWeight = set.getWeightKg();
        Integer setReps = set.getReps();

        return getCurrentOrCreateEmptyPR(userId, exerciseId, "MAX_REPS")
                .flatMap(existingPR -> {
                    // Only count as PR if weight is same or higher and reps are more
                    BigDecimal existingWeight = existingPR.getWeight() != null ? existingPR.getWeight() : BigDecimal.ZERO;
                    Integer existingReps = existingPR.getReps() != null ? existingPR.getReps() : 0;

                    boolean isNewPR = existingPR.getValue() == null ||
                            (setWeight.compareTo(existingWeight) >= 0 && setReps > existingReps);

                    if (isNewPR) {
                        return upsertPersonalRecord(userId, exerciseId, exerciseName, set, "MAX_REPS",
                                BigDecimal.valueOf(setReps),
                                existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Check for estimated 1RM PR from a set using upsert with exercise name
     */
    private Mono<PersonalRecord> checkEstimated1RMPRFromSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        BigDecimal estimated1RM = calculate1RM(set.getWeightKg(), set.getReps());

        return getCurrentOrCreateEmptyPR(userId, exerciseId, "ESTIMATED_1RM")
                .flatMap(existingPR -> {
                    BigDecimal existing1RM = existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO;

                    if (estimated1RM.compareTo(existing1RM) > 0) {
                        return upsertPersonalRecord(userId, exerciseId, exerciseName, set, "ESTIMATED_1RM",
                                estimated1RM, existing1RM);
                    }
                    return Mono.empty();
                });
    }

    /**
     * CRITICAL FIX: Get current PR or create empty one for comparison
     */
    private Mono<PersonalRecord> getCurrentOrCreateEmptyPR(Long userId, String exerciseId, String recordType) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType)
                .switchIfEmpty(Mono.just(createEmptyPR()));
    }

    /**
     * CRITICAL FIX: Upsert personal record with proper exercise name
     * This prevents the E11000 duplicate key error by using findAndModify with upsert
     */
    private Mono<PersonalRecord> upsertPersonalRecord(Long userId, String exerciseId, String exerciseName,
                                                      WorkoutSet set, String recordType, BigDecimal value, BigDecimal previousValue) {

        // Create query to find existing record
        Query query = Query.query(
                Criteria.where("userId").is(userId)
                        .and("exerciseId").is(exerciseId)
                        .and("recordType").is(recordType)
        );

        // Calculate improvement percentage
        Double improvementPercentage = null;
        if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal improvement = value.subtract(previousValue)
                    .divide(previousValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            improvementPercentage = improvement.doubleValue();
        }

        // Create update with the new values
        Update update = new Update()
                .set("value", value)
                .set("weightKg", set.getWeightKg())
                .set("reps", set.getReps())
                .set("sets", 1)
                .set("achievedDate", LocalDateTime.now())
                .set("previousRecord", previousValue)
                .set("improvementPercentage", improvementPercentage)
                .set("exerciseName", exerciseName)
                // Set on insert only - these won't change if record exists
                .setOnInsert("userId", userId)
                .setOnInsert("exerciseId", exerciseId)
                .setOnInsert("recordType", recordType);

        // Use findAndModify with upsert option
        FindAndModifyOptions options = new FindAndModifyOptions()
                .upsert(true)
                .returnNew(true);

        log.info("Attempting to upsert {} PR for user {} exercise '{}' ({}): {} (previous: {})",
                recordType, userId, exerciseName, exerciseId, value, previousValue);

        return reactiveMongoTemplate
                .findAndModify(query, update, options, PersonalRecord.class)
                .doOnSuccess(savedRecord -> {
                    if (savedRecord != null) {
                        log.info("Successfully upserted {} PR: userId={}, exercise='{}', value={}",
                                recordType, savedRecord.getUserId(), savedRecord.getExerciseName(), savedRecord.getValue());
                    }
                })
                .onErrorResume(DuplicateKeyException.class, error -> {
                    // If we still get a duplicate key error, try to find the existing record
                    log.warn("Duplicate key error during upsert for {} PR, attempting to find existing record: {}",
                            recordType, error.getMessage());
                    return personalRecordRepository
                            .findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType)
                            .doOnSuccess(existingRecord -> {
                                if (existingRecord != null) {
                                    log.debug("Found existing {} PR record instead of creating new one", recordType);
                                }
                            })
                            .switchIfEmpty(Mono.error(new RuntimeException("Failed to upsert and find existing record", error)));
                });
    }

    /**
     * Get recent PRs for a user
     */
    public Mono<List<PersonalRecord>> getRecentPRs(Long userId, int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        return personalRecordRepository
                .findByUserIdAndAchievedDateGreaterThanOrderByAchievedDateDesc(userId, cutoffDate)
                .collectList();
    }

    /**
     * Get all PRs for a user
     */
    public Flux<PersonalRecord> getAllPRs(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId);
    }

    /**
     * Get PRs for a specific exercise
     */
    public Flux<PersonalRecord> getExercisePRs(Long userId, String exerciseId) {
        return personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedDateDesc(userId, exerciseId);
    }

    /**
     * Get current PR for an exercise and record type
     */
    public Mono<PersonalRecord> getCurrentPR(Long userId, String exerciseId, String recordType) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType);
    }

    /**
     * Get PR statistics for a user
     */
    public Mono<PRStatistics> getPRStatistics(Long userId) {
        return Mono.zip(
                        personalRecordRepository.countByUserId(userId),
                        personalRecordRepository.countByUserIdAndAchievedDateBetween(
                                userId, LocalDateTime.now().minusDays(30), LocalDateTime.now()),
                        personalRecordRepository.countByUserIdAndAchievedDateBetween(
                                userId, LocalDateTime.now().minusDays(7), LocalDateTime.now()))
                .map(tuple -> {
                    PRStatistics stats = new PRStatistics();
                    stats.setTotalPRs(tuple.getT1().intValue());
                    stats.setPRsLast30Days(tuple.getT2().intValue());
                    stats.setPRsLast7Days(tuple.getT3().intValue());
                    return stats;
                });
    }

    /**
     * Check if a potential lift would be a PR
     */
    public Mono<Boolean> wouldBePR(Long userId, String exerciseId, String recordType, BigDecimal value) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType)
                .map(existingPR -> existingPR.getValue() == null || value.compareTo(existingPR.getValue()) > 0)
                .defaultIfEmpty(true); // If no existing PR, any value would be a PR
    }

    /**
     * Delete a PR (in case of error or dispute)
     */
    public Mono<Void> deletePR(String prId, Long userId) {
        return personalRecordRepository.findById(prId)
                .filter(pr -> pr.getUserId().equals(userId)) // Security check
                .flatMap(personalRecordRepository::delete)
                .doOnSuccess(v -> log.info("Deleted PR {} for user {}", prId, userId));
    }

    // HELPER METHODS

    /**
     * Create an empty PersonalRecord for comparison
     */
    private PersonalRecord createEmptyPR() {
        return PersonalRecord.builder()
                .value(null)
                .previousRecord(null)
                .build();
    }

    /**
     * Calculate 1RM using Epley formula
     */
    private BigDecimal calculate1RM(BigDecimal weight, int reps) {
        if (reps == 1) return weight;

        // Epley formula: weight * (1 + reps/30)
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
        return weight.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determine unit based on record type
     */
    private String determineUnit(String recordType) {
        return switch (recordType) {
            case "MAX_WEIGHT", "ESTIMATED_1RM" -> "kg";
            case "MAX_VOLUME" -> "kg";
            case "MAX_REPS" -> "reps";
            default -> "units";
        };
    }

    // INNER CLASSES

    /**
     * PR statistics data class
     */
    @Data
    @Getter
    @Setter
    public static class PRStatistics {
        private Integer totalPRs;
        private Integer pRsLast30Days;
        private Integer pRsLast7Days;

        // Explicit getters for consistent naming
        public Integer getPRsLast30Days() { return pRsLast30Days; }
        public void setPRsLast30Days(Integer pRsLast30Days) { this.pRsLast30Days = pRsLast30Days; }
        public Integer getPRsLast7Days() { return pRsLast7Days; }
        public void setPRsLast7Days(Integer pRsLast7Days) { this.pRsLast7Days = pRsLast7Days; }
    }

 }