package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.dto.response.analytics.PRStatistics;
import com.muscledia.workout_service.event.PersonalRecordEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.repository.WorkoutRepository;
import com.muscledia.workout_service.repository.analytics.PersonalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Complete PersonalRecordService
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService implements IPersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;
    private final WorkoutRepository workoutRepository;
    private final TransactionalEventPublisher eventPublisher;

    /**
     * UPDATED: Now returns List<PersonalRecordEvent> instead of Void
     * Implements the interface method that was previously returning null
     */
    @Override
    public Mono<List<PersonalRecordEvent>> processWorkoutForPersonalRecords(String workoutId, Long userId) {
        log.debug("Processing workout {} for personal records", workoutId);

        return workoutRepository.findById(workoutId)
                .filter(workout -> workout.getUserId().equals(userId)) // Security check
                .flatMap(this::processWorkoutForPersonalRecords)
                .doOnSuccess(events -> log.debug("Found {} personal records for workout {}",
                        events.size(), workoutId))
                .doOnError(error -> log.error("Error processing PRs for workout {}: {}",
                        workoutId, error.getMessage()));
    }

    /**
     * UPDATED: Main method now returns List<PersonalRecordEvent> instead of Void
     * Keeps ALL your existing logic but collects events instead of publishing
     */
    @Override
    public Mono<List<PersonalRecordEvent>> processWorkoutForPersonalRecords(Workout workout) {
        log.debug("Processing workout {} for personal records", workout.getId());

        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> processExerciseForPRs(workout, exercise))
                .collectList() // Collect all PersonalRecordEvent objects
                .doOnSuccess(events -> log.debug("Completed PR processing for workout {}, found {} PRs",
                        workout.getId(), events.size()))
                .doOnError(error -> log.error("Error processing PRs for workout {}: {}",
                        workout.getId(), error.getMessage()));
    }

    /**
     * IMMEDIATE SET PR PROCESSING (REAL-TIME FEEDBACK)
     *
     * CLEAN ARCHITECTURE:
     * 1. Detect PRs (business logic)
     * 2. Create events (domain logic)
     * 3. Publish via existing TransactionalEventPublisher (infrastructure)
     */
    public Mono<List<PersonalRecordEvent>> processSetForImmediatePRs(Long userId, String exerciseId,
                                                                     String exerciseName, WorkoutSet set,
                                                                     String workoutId) {
        log.debug("🔍Processing set for immediate PRs: user={}, exercise={}, weight={}, reps={}",
                userId, exerciseId, set.getWeightKg(), set.getReps());

        // Business validation
        if (!shouldProcessSetForPRs(set)) {
            return Mono.just(Collections.emptyList());
        }

        return detectAndCreatePREvents(userId, exerciseId, exerciseName, set, workoutId)
                .flatMap(this::publishPersonalRecordEventsUsingExistingPublisher)  // Use existing method!
                .doOnSuccess(events -> {
                    if (!events.isEmpty()) {
                        log.info("IMMEDIATE PR PIPELINE: Detected and published {} PRs for user {}", events.size(), userId);
                    }
                });
    }

    /**
     * CORE PR DETECTION LOGIC (Pure business logic)
     */
    private Mono<List<PersonalRecordEvent>> detectAndCreatePREvents(Long userId, String exerciseId,
                                                                    String exerciseName, WorkoutSet set,
                                                                    String workoutId) {
        List<Mono<PersonalRecordEvent>> prChecks = new ArrayList<>();

        // Check all PR types using clean separation
        if (shouldCheckWeightPR(set)) {
            prChecks.add(checkAndCreateWeightPR(userId, exerciseId, exerciseName, set, workoutId));
        }

        if (shouldCheckRepsPR(set)) {
            prChecks.add(checkAndCreateRepsPR(userId, exerciseId, exerciseName, set, workoutId));
        }

        if (shouldCheckVolumePR(set)) {
            prChecks.add(checkAndCreateVolumePR(userId, exerciseId, exerciseName, set, workoutId));
        }

        if (shouldCheckEstimated1RMPR(set)) {
            prChecks.add(checkAndCreateEstimated1RMPR(userId, exerciseId, exerciseName, set, workoutId));
        }

        return Flux.mergeSequential(prChecks)
                .filter(Objects::nonNull)
                .collectList();
    }

    /**
     * EVENT PUBLISHING using EXISTING TransactionalEventPublisher.publishPersonalRecord()
     *
     * PERFECT PATTERN MATCH with WorkoutCompleted events!
     */
    private Mono<List<PersonalRecordEvent>> publishPersonalRecordEventsUsingExistingPublisher(List<PersonalRecordEvent> events) {
        if (events.isEmpty()) {
            return Mono.just(events);
        }

        return Flux.fromIterable(events)
                .flatMap(event ->
                        // EXISTING METHOD: Uses same pattern as publishWorkoutCompleted()
                        eventPublisher.publishPersonalRecord(event)
                                .doOnSuccess(v -> log.info("Published PersonalRecord event: {} for user {}",
                                        event.getEventId(), event.getUserId()))
                                .doOnError(error -> log.error("Failed to publish PersonalRecord event: {}", error.getMessage()))
                                .then(Mono.just(event))
                )
                .collectList()
                .doOnSuccess(publishedEvents -> log.info("Published {} PersonalRecord events using existing infrastructure",
                        publishedEvents.size()));
    }

    // ==================== PR TYPE CHECKERS (Single Responsibility) ====================

    private Mono<PersonalRecordEvent> checkAndCreateWeightPR(Long userId, String exerciseId, String exerciseName,
                                                             WorkoutSet set, String workoutId) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_WEIGHT")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_WEIGHT"))
                .flatMap(existingPR -> {
                    if (set.getWeightKg().compareTo(existingPR.getValue()) > 0) {
                        log.info("MAX_WEIGHT PR: {}kg (was {}kg) for exercise {}",
                                set.getWeightKg(), existingPR.getValue(), exerciseName);
                        return updatePersonalRecordAndCreateEvent(existingPR, set.getWeightKg(), set.getReps(),
                                exerciseName, workoutId, exerciseId, "MAX_WEIGHT");
                    }
                    return Mono.empty();
                });
    }

    private Mono<PersonalRecordEvent> checkAndCreateRepsPR(Long userId, String exerciseId, String exerciseName,
                                                           WorkoutSet set, String workoutId) {
        BigDecimal newReps = BigDecimal.valueOf(set.getReps());
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_REPS")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_REPS"))
                .flatMap(existingPR -> {
                    if (newReps.compareTo(existingPR.getValue()) > 0) {
                        log.info("MAX_REPS PR: {} reps (was {} reps) for exercise {}",
                                set.getReps(), existingPR.getValue().intValue(), exerciseName);
                        return updatePersonalRecordAndCreateEvent(existingPR, newReps, set.getReps(),
                                exerciseName, workoutId, exerciseId, "MAX_REPS");
                    }
                    return Mono.empty();
                });
    }

    private Mono<PersonalRecordEvent> checkAndCreateVolumePR(Long userId, String exerciseId, String exerciseName,
                                                             WorkoutSet set, String workoutId) {
        BigDecimal volume = set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps()));
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_VOLUME")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_VOLUME"))
                .flatMap(existingPR -> {
                    if (volume.compareTo(existingPR.getValue()) > 0) {
                        log.info("MAX_VOLUME PR: {}kg total (was {}kg) for exercise {}",
                                volume, existingPR.getValue(), exerciseName);
                        return updatePersonalRecordAndCreateEvent(existingPR, volume, set.getReps(),
                                exerciseName, workoutId, exerciseId, "MAX_VOLUME");
                    }
                    return Mono.empty();
                });
    }

    private Mono<PersonalRecordEvent> checkAndCreateEstimated1RMPR(Long userId, String exerciseId, String exerciseName,
                                                                   WorkoutSet set, String workoutId) {
        BigDecimal estimated1RM = calculateEstimated1RM(set.getWeightKg(), set.getReps());
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "ESTIMATED_1RM")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "ESTIMATED_1RM"))
                .flatMap(existingPR -> {
                    if (estimated1RM.compareTo(existingPR.getValue()) > 0) {
                        log.info("ESTIMATED_1RM PR: {}kg (was {}kg) for exercise {}",
                                estimated1RM, existingPR.getValue(), exerciseName);
                        return updatePersonalRecordAndCreateEvent(existingPR, estimated1RM, set.getReps(),
                                exerciseName, workoutId, exerciseId, "ESTIMATED_1RM");
                    }
                    return Mono.empty();
                });
    }

    // ==================== DOMAIN LOGIC (Pure Functions) ====================

    private PersonalRecord createEmptyPR(Long userId, String exerciseId, String exerciseName, String recordType) {
        return PersonalRecord.builder()
                .userId(userId)
                .exerciseId(exerciseId)
                .exerciseName(exerciseName)
                .recordType(recordType)
                .value(BigDecimal.ZERO)
                .achievedDate(LocalDateTime.now())
                .build();
    }

    private Mono<PersonalRecordEvent> updatePersonalRecordAndCreateEvent(PersonalRecord existingPR, BigDecimal newValue,
                                                                         Integer reps, String exerciseName, String workoutId,
                                                                         String exerciseId, String recordType) {
        // Domain logic
        BigDecimal previousValue = existingPR.getValue();
        existingPR.setPreviousRecord(previousValue);
        existingPR.setValue(newValue);
        existingPR.setReps(reps);
        existingPR.setWorkoutId(workoutId);
        existingPR.setExerciseId(exerciseId);
        existingPR.setAchievedDate(LocalDateTime.now());

        // Calculate improvement percentage
        if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal improvement = newValue.subtract(previousValue)
                    .divide(previousValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            existingPR.setImprovementPercentage(improvement.doubleValue());
        }

        return personalRecordRepository.save(existingPR)
                .map(savedPR -> createPersonalRecordEvent(savedPR, exerciseName, workoutId, exerciseId))
                .doOnSuccess(event -> log.debug("PersonalRecord saved and event created: {}", event.getEventId()));
    }

    private PersonalRecordEvent createPersonalRecordEvent(PersonalRecord pr, String exerciseName,
                                                          String workoutId, String exerciseId) {
        return PersonalRecordEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(pr.getUserId())
                .exerciseName(exerciseName != null ? exerciseName : "Unknown Exercise")
                .exerciseId(exerciseId)
                .recordType(pr.getRecordType())
                .newValue(pr.getValue())
                .previousValue(pr.getPreviousRecord() != null ? pr.getPreviousRecord() : BigDecimal.ZERO)
                .unit(getUnitForRecordType(pr.getRecordType()))
                .workoutId(workoutId)
                .reps(pr.getReps())
                .achievedAt(Instant.now())
                .timestamp(Instant.now())
                .source("workout-service")
                .build();
    }

    // ==================== VALIDATION HELPERS ====================

    private boolean shouldProcessSetForPRs(WorkoutSet set) {
        return Boolean.TRUE.equals(set.getCompleted()) && set.countsTowardPersonalRecords();
    }

    private boolean shouldCheckWeightPR(WorkoutSet set) {
        return set.getWeightKg() != null && set.getWeightKg().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean shouldCheckRepsPR(WorkoutSet set) {
        return set.getReps() != null && set.getReps() > 0;
    }

    private boolean shouldCheckVolumePR(WorkoutSet set) {
        return shouldCheckWeightPR(set) && shouldCheckRepsPR(set);
    }

    private boolean shouldCheckEstimated1RMPR(WorkoutSet set) {
        return shouldCheckVolumePR(set) && set.getReps() <= 10;
    }

    private BigDecimal calculateEstimated1RM(BigDecimal weight, Integer reps) {
        if (reps == 1) {
            return weight;
        }
        double multiplier = 1.0 + (reps.doubleValue() / 30.0);
        return weight.multiply(BigDecimal.valueOf(multiplier));
    }

    private String getUnitForRecordType(String recordType) {
        return switch (recordType) {
            case "MAX_WEIGHT", "ESTIMATED_1RM" -> "kg";
            case "MAX_REPS" -> "reps";
            case "MAX_VOLUME" -> "kg";
            default -> "units";
        };
    }

    // ==================== EXERCISE-LEVEL PROCESSING (WORKOUT COMPLETION) ====================

    private Flux<PersonalRecordEvent> processExerciseForPRs(Workout workout, WorkoutExercise exercise) {
        return Flux.fromIterable(exercise.getSets())
                .filter(set -> Boolean.TRUE.equals(set.getCompleted()))
                .flatMap(set -> detectAndCreatePREvents(
                        workout.getUserId(),
                        exercise.getExerciseId(),
                        exercise.getExerciseName(),
                        set,
                        workout.getId()
                ))
                .flatMap(Flux::fromIterable);
    }

    // ==================== QUERY OPERATIONS (UNCHANGED) ====================

    @Override
    public Flux<PersonalRecord> getExercisePRs(Long userId, String exerciseId) {
        return personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedDateDesc(userId, exerciseId);
    }

    @Override
    public Flux<PersonalRecord> getAllPersonalRecords(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId);
    }

    @Override
    public Flux<PersonalRecord> getPersonalRecords(Long userId, String exerciseId) {
        return personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedDateDesc(userId, exerciseId);
    }

    @Override
    public Mono<PersonalRecord> getPersonalRecord(Long userId, String exerciseId, String recordType) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType);
    }

    @Override
    public Mono<List<PersonalRecord>> getRecentPRs(Long userId, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return personalRecordRepository.findByUserIdAndAchievedDateGreaterThanOrderByAchievedDateDesc(userId, cutoff)
                .collectList();
    }

    @Override
    public Mono<PRStatistics> getPRStatistics(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId)
                .collectList()
                .map(this::calculatePRStatistics);
    }

    @Override
    public Flux<PersonalRecord> getAllPRs(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId);
    }

    @Override
    public Mono<Boolean> wouldBePR(Long userId, String exerciseId, String recordType, BigDecimal value) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType)
                .map(existingPR -> existingPR.getValue() == null || value.compareTo(existingPR.getValue()) > 0)
                .defaultIfEmpty(true);
    }

    @Override
    public Mono<Void> deletePR(String prId, Long userId) {
        return personalRecordRepository.findById(prId)
                .filter(pr -> pr.getUserId().equals(userId))
                .flatMap(personalRecordRepository::delete)
                .doOnSuccess(v -> log.info("Deleted PR {} for user {}", prId, userId));
    }

    @Override
    public Mono<List<String>> detectQualifyingPRsForSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        // Implementation for detection without actual PR creation
        return Mono.just(Collections.emptyList()); // Implement if needed
    }

    private PRStatistics calculatePRStatistics(List<PersonalRecord> prs) {
        if (prs.isEmpty()) {
            return PRStatistics.builder()
                    .totalPRs(0)
                    .prsThisMonth(0)
                    .prsThisWeek(0)
                    .prsByType(Map.of())
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusWeeks(1);
        LocalDateTime monthAgo = now.minusMonths(1);

        int prsThisWeek = (int) prs.stream()
                .filter(pr -> pr.getAchievedDate().isAfter(weekAgo))
                .count();

        int prsThisMonth = (int) prs.stream()
                .filter(pr -> pr.getAchievedDate().isAfter(monthAgo))
                .count();

        Map<String, Integer> prsByType = prs.stream()
                .collect(Collectors.groupingBy(
                        PersonalRecord::getRecordType,
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));

        double averageImprovement = prs.stream()
                .filter(pr -> pr.getImprovementPercentage() != null)
                .mapToDouble(PersonalRecord::getImprovementPercentage)
                .average()
                .orElse(0.0);

        return PRStatistics.builder()
                .totalPRs(prs.size())
                .prsThisWeek(prsThisWeek)
                .prsThisMonth(prsThisMonth)
                .lastPRDate(prs.isEmpty() ? null : prs.get(0).getAchievedDate())
                .prsByType(prsByType)
                .averageImprovement(averageImprovement)
                .build();
    }
 }