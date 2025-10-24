package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.dto.response.analytics.PRStatistics;
import com.muscledia.workout_service.event.PersonalRecordEvent;
import com.muscledia.workout_service.event.publisher.TransactionalEventPublisher;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Complete PersonalRecordService
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;
    private final TransactionalEventPublisher eventPublisher;

    /**
     * Main method called from WorkoutService.completeWorkout()
     */
    public Mono<Void> processWorkoutForPersonalRecords(Workout workout) {
        log.debug("Processing workout {} for personal records", workout.getId());

        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> processExerciseForPRs(workout, exercise))
                .then()
                .doOnSuccess(v -> log.debug("Completed PR processing for workout {}", workout.getId()))
                .doOnError(error -> log.error("Error processing PRs for workout {}: {}", workout.getId(), error.getMessage()));
    }

    /**
     * Process exercise for PRs with exercise name
     */
    private Mono<Void> processExerciseForPRs(Workout workout, WorkoutExercise exercise) {
        return checkAndUpdatePersonalRecordsWithRetry(
                workout.getUserId(),
                exercise.getExerciseId(),
                exercise.getExerciseName(),
                exercise.getSets(),
                workout.getId()
        );
    }

    /**
     * Check and update personal records with retry logic
     */
    public Mono<Void> checkAndUpdatePersonalRecordsWithRetry(Long userId, String exerciseId, String exerciseName,
                                                             List<WorkoutSet> sets, String workoutId) {
        log.debug("Checking PRs for user {} exercise {} with {} sets", userId, exerciseId, sets.size());

        return Flux.fromIterable(sets)
                .filter(set -> Boolean.TRUE.equals(set.getCompleted()))
                .flatMap(set -> checkSetForAllPRs(userId, exerciseId, exerciseName, set))
                .then()
                .doOnSuccess(v -> log.debug("Completed PR check for exercise {} with {} sets", exerciseId, sets.size()))
                .doOnError(error -> log.error("Error checking PRs for exercise {}: {}", exerciseId, error.getMessage()));
    }

    /**
     * Detect qualifying PRs for a set WITHOUT updating them - for real-time detection during set logging
     */
    public Mono<List<String>> detectQualifyingPRsForSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        // Only check completed sets
        if (!Boolean.TRUE.equals(set.getCompleted())) {
            return Mono.just(List.of());
        }

        List<Mono<String>> prChecks = new ArrayList<>();

        // Check MAX_WEIGHT PR
        if (set.getWeightKg() != null && set.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
            Mono<String> weightCheck = personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_WEIGHT")
                    .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_WEIGHT"))
                    .mapNotNull(existingPR -> {
                        if (set.getWeightKg().compareTo(existingPR.getValue()) > 0) {
                            return "MAX_WEIGHT: " + set.getWeightKg() + "kg (was " + existingPR.getValue() + "kg)";
                        }
                        return null;
                    })
                    .filter(Objects::nonNull);
            prChecks.add(weightCheck);
        }

        // Check MAX_REPS PR
        if (set.getReps() != null && set.getReps() > 0) {
            Mono<String> repsCheck = personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_REPS")
                    .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_REPS"))
                    .mapNotNull(existingPR -> {
                        BigDecimal newReps = BigDecimal.valueOf(set.getReps());
                        if (newReps.compareTo(existingPR.getValue()) > 0) {
                            return "MAX_REPS: " + set.getReps() + " reps (was " + existingPR.getValue().intValue() + " reps)";
                        }
                        return null;
                    })
                    .filter(Objects::nonNull);
            prChecks.add(repsCheck);
        }

        // Check MAX_VOLUME PR
        if (set.getWeightKg() != null && set.getReps() != null &&
                set.getWeightKg().compareTo(BigDecimal.ZERO) > 0 && set.getReps() > 0) {

            BigDecimal volume = set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps()));

            Mono<String> volumeCheck = personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_VOLUME")
                    .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_VOLUME"))
                    .mapNotNull(existingPR -> {
                        if (volume.compareTo(existingPR.getValue()) > 0) {
                            return "MAX_VOLUME: " + volume + "kg (was " + existingPR.getValue() + "kg)";
                        }
                        return null;
                    })
                    .filter(Objects::nonNull);
            prChecks.add(volumeCheck);
        }

        // Check ESTIMATED_1RM PR
        if (set.getWeightKg() != null && set.getReps() != null &&
                set.getWeightKg().compareTo(BigDecimal.ZERO) > 0 && set.getReps() > 0 && set.getReps() <= 10) {

            BigDecimal estimated1RM = calculateEstimated1RM(set.getWeightKg(), set.getReps());

            Mono<String> e1rmCheck = personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "ESTIMATED_1RM")
                    .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "ESTIMATED_1RM"))
                    .mapNotNull(existingPR -> {
                        if (estimated1RM.compareTo(existingPR.getValue()) > 0) {
                            return "ESTIMATED_1RM: " + estimated1RM + "kg (was " + existingPR.getValue() + "kg)";
                        }
                        return null;
                    })
                    .filter(Objects::nonNull);
            prChecks.add(e1rmCheck);
        }

        // Combine all checks and return list of qualifying PRs
        return Flux.mergeSequential(prChecks)
                .collectList();
    }

    /**
     * Check a single set for all types of PRs
     */
    private Mono<Void> checkSetForAllPRs(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        return Mono.empty()
                .then(Mono.defer(() -> {
                    // Check MAX_WEIGHT PR
                    if (set.getWeightKg() != null && set.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                        return checkWeightPR(userId, exerciseId, exerciseName, set);
                    }
                    return Mono.empty();
                }))
                .then(Mono.defer(() -> {
                    // Check MAX_REPS PR
                    if (set.getReps() != null && set.getReps() > 0) {
                        return checkRepsPR(userId, exerciseId, exerciseName, set);
                    }
                    return Mono.empty();
                }))
                .then(Mono.defer(() -> {
                    // Check MAX_VOLUME PR (weight × reps)
                    if (set.getWeightKg() != null && set.getReps() != null &&
                            set.getWeightKg().compareTo(BigDecimal.ZERO) > 0 && set.getReps() > 0) {
                        BigDecimal volume = set.getWeightKg().multiply(BigDecimal.valueOf(set.getReps()));
                        return checkVolumePR(userId, exerciseId, exerciseName, set, volume);
                    }
                    return Mono.empty();
                }))
                .then(Mono.defer(() -> {
                    // Check ESTIMATED_1RM PR if we have enough data
                    if (set.getWeightKg() != null && set.getReps() != null &&
                            set.getWeightKg().compareTo(BigDecimal.ZERO) > 0 && set.getReps() > 0 && set.getReps() <= 10) {
                        BigDecimal estimated1RM = calculateEstimated1RM(set.getWeightKg(), set.getReps());
                        return checkEstimated1RMPR(userId, exerciseId, exerciseName, set, estimated1RM);
                    }
                    return Mono.empty();
                }));
    }

    /**
     * Check weight PR
     */
    private Mono<Void> checkWeightPR(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_WEIGHT")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_WEIGHT"))
                .flatMap(existingPR -> {
                    if (set.getWeightKg().compareTo(existingPR.getValue()) > 0) {
                        return updatePR(userId, exerciseId, exerciseName, "MAX_WEIGHT",
                                set.getWeightKg(), set.getReps(), set.getWeightKg(),null);
                    }
                    return Mono.empty();
                });
    }

    /**
     * Check reps PR
     */
    private Mono<Void> checkRepsPR(Long userId, String exerciseId, String exerciseName, WorkoutSet set) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_REPS")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_REPS"))
                .flatMap(existingPR -> {
                    BigDecimal newReps = BigDecimal.valueOf(set.getReps());
                    if (newReps.compareTo(existingPR.getValue()) > 0) {
                        return updatePR(userId, exerciseId, exerciseName, "MAX_REPS",
                                newReps, set.getReps(), set.getWeightKg(), null);
                    }
                    return Mono.empty();
                });
    }

    /**
     * Check volume PR
     */
    private Mono<Void> checkVolumePR(Long userId, String exerciseId, String exerciseName, WorkoutSet set, BigDecimal volume) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "MAX_VOLUME")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "MAX_VOLUME"))
                .flatMap(existingPR -> {
                    if (volume.compareTo(existingPR.getValue()) > 0) {
                        return updatePR(userId, exerciseId, exerciseName, "MAX_VOLUME",
                                volume, set.getReps(), set.getWeightKg(), null);
                    }
                    return Mono.empty();
                });
    }

    /**
     * Check estimated 1RM PR
     */
    private Mono<Void> checkEstimated1RMPR(Long userId, String exerciseId, String exerciseName, WorkoutSet set, BigDecimal estimated1RM) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, "ESTIMATED_1RM")
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, "ESTIMATED_1RM"))
                .flatMap(existingPR -> {
                    if (estimated1RM.compareTo(existingPR.getValue()) > 0) {
                        return updatePR(userId, exerciseId, exerciseName, "ESTIMATED_1RM",
                                estimated1RM, set.getReps(), set.getWeightKg(), null);
                    }
                    return Mono.empty();
                });
    }

    /**
     * Create empty PR for comparison
     */
    private PersonalRecord createEmptyPR(Long userId, String exerciseId, String exerciseName, String recordType) {
        return PersonalRecord.builder()
                .userId(userId)
                .exerciseId(exerciseId)
                .exerciseName(exerciseName)
                .recordType(recordType)
                .value(BigDecimal.ZERO)
                .verified(true)
                .build();
    }

    /**
     * Update/create a personal record and publish event
     */
    private Mono<Void> updatePR(Long userId, String exerciseId, String exerciseName,
                                String recordType, BigDecimal newValue,
                                Integer reps, BigDecimal weight, String workoutId) {

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType)
                .defaultIfEmpty(createEmptyPR(userId, exerciseId, exerciseName, recordType))
                .flatMap(existingPR -> {
                    // Check if this is actually a new record
                    if (existingPR.getValue() != null && newValue.compareTo(existingPR.getValue()) <= 0) {
                        log.debug("Value {} is not better than existing {} for {}", newValue, existingPR.getValue(), recordType);
                        return Mono.empty();
                    }

                    BigDecimal previousValue = existingPR.getValue();

                    // Update the record
                    existingPR.setPreviousRecord(previousValue);
                    existingPR.setValue(newValue);
                    existingPR.setAchievedDate(LocalDateTime.now());
                    existingPR.setReps(reps);
                    existingPR.setWeight(weight);
                    existingPR.setWorkoutId(workoutId);
                    existingPR.setExerciseId(exerciseId); // Make sure this is set

                    if (previousValue != null) {
                        BigDecimal improvement = newValue.subtract(previousValue)
                                .divide(previousValue, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        existingPR.setImprovementPercentage(improvement.doubleValue());
                    }

                    return personalRecordRepository.save(existingPR)
                            .flatMap(savedPR -> {
                                log.info("New {} PR for user {} exercise {}: {} (was {})",
                                        savedPR.getRecordType(), userId, exerciseId, newValue, previousValue);
                                return publishPersonalRecordEvent(savedPR, exerciseName);
                            });
                })
                .then();
    }

    /**
     * Calculate estimated 1RM using Epley formula
     */
    private BigDecimal calculateEstimated1RM(BigDecimal weight, Integer reps) {
        if (reps == 1) {
            return weight;
        }
        double multiplier = 1.0 + (reps.doubleValue() / 30.0);
        return weight.multiply(BigDecimal.valueOf(multiplier));
    }

    /**
     * Publish PersonalRecordEvent - FIXED to use correct types
     */
    private Mono<Void> publishPersonalRecordEvent(PersonalRecord pr, String exerciseName) {
        PersonalRecordEvent event = PersonalRecordEvent.builder()
                .userId(pr.getUserId())
                .exerciseName(exerciseName)
                .exerciseId(pr.getExerciseId())
                .recordType(pr.getRecordType())
                .newValue(BigDecimal.valueOf(pr.getValue().doubleValue()))
                .previousValue(pr.getPreviousRecord() != null ? pr.getPreviousRecord() : BigDecimal.ZERO)
                .unit(getUnitForRecordType(pr.getRecordType()))
                .workoutId(pr.getWorkoutId())
                .reps(pr.getReps())
                .achievedAt(Instant.now())
                .build();

        // Use YOUR existing publishPersonalRecord method
        return eventPublisher.publishPersonalRecord(event)
                .doOnSuccess(v -> log.debug("Published PersonalRecordEvent for {} PR", pr.getRecordType()))
                .doOnError(error -> log.warn("Failed to publish PersonalRecordEvent: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty()); // Don't fail PR saving if event publishing fails
    }

    /**
     * Get unit for record type
     */
    private String getUnitForRecordType(String recordType) {
        return switch (recordType) {
            case "MAX_WEIGHT", "ESTIMATED_1RM" -> "kg";
            case "MAX_REPS" -> "reps";
            case "MAX_VOLUME" -> "kg";
            default -> "units";
        };
    }

    // PUBLIC QUERY METHODS (these fix the missing method compilation errors)

    /**
     * Get exercise PRs - fixes the missing method error
     */
    public Flux<PersonalRecord> getExercisePRs(Long userId, String exerciseId) {
        return personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedDateDesc(userId, exerciseId);
    }

    /**
     * Get all PRs for a user
     */
    public Flux<PersonalRecord> getAllPersonalRecords(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId);
    }

    /**
     * Get PRs for specific exercise
     */
    public Flux<PersonalRecord> getPersonalRecords(Long userId, String exerciseId) {
        return personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedDateDesc(userId, exerciseId);
    }

    /**
     * Get specific PR type
     */
    public Mono<PersonalRecord> getPersonalRecord(Long userId, String exerciseId, String recordType) {
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(userId, exerciseId, recordType);
    }

    /**
     * Get recent PRs for analytics
     */
    public Mono<List<PersonalRecord>> getRecentPRs(Long userId, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return personalRecordRepository.findByUserIdAndAchievedDateGreaterThanOrderByAchievedDateDesc(userId, cutoff)
                .collectList();
    }

    /**
     * Get PR statistics - fixes the missing getPRStatistics method
     */
    public Mono<PRStatistics> getPRStatistics(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId)
                .collectList()
                .map(this::calculatePRStatistics);
    }

    /**
     * Calculate PR statistics from PR list - FIXED the compatibility issue
     */
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
                .lastPRDate(prs.isEmpty() ? null : prs.getFirst().getAchievedDate()) // FIXED: Use get(0) for compatibility
                .prsByType(prsByType)
                .averageImprovement(averageImprovement)
                .build();
    }


    /**
     * Get all PRs for a user
     */
    public Flux<PersonalRecord> getAllPRs(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedDateDesc(userId);
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
 }