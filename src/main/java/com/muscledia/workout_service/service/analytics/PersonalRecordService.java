package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.repository.analytics.PersonalRecordRepository;
import com.muscledia.workout_service.service.ExerciseService;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;
    private final ExerciseService exerciseService;

    /**
     * Process a workout and detect any new personal records
     */
    public Mono<List<PersonalRecord>> processWorkoutForPRs(Workout workout) {
        log.info("Processing workout {} for PRs for user {}", workout.getId(), workout.getUserId());

        return Flux.fromIterable(workout.getExercises())
                .flatMap(exercise -> checkForPRs(workout.getUserId(), exercise, workout))
                .collectList()
                .doOnSuccess(prs -> log.info("Found {} new PRs for workout {}", prs.size(), workout.getId()));
    }

    /**
     * FIXED: Check and update personal records for an exercise's sets
     */
    public Mono<List<PersonalRecord>> checkAndUpdatePersonalRecords(Long userId, String exerciseId, List<WorkoutSet> sets) {
        log.info("Checking PRs for user {} exercise {} with {} sets", userId, exerciseId, sets.size());

        if (sets.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(sets)
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .flatMap(set -> checkSetForPRs(userId, exerciseId, set))
                .collectList()
                .doOnSuccess(prs -> log.info("Found {} new PRs for exercise {}", prs.size(), exerciseId));
    }

    /**
     * Check for personal records in a single set
     */
    private Flux<PersonalRecord> checkSetForPRs(Long userId, String exerciseId, WorkoutSet set) {
        return Flux.concat(
                checkMaxWeightPRFromSet(userId, exerciseId, set),
                checkMaxVolumePRFromSet(userId, exerciseId, set),
                checkMaxRepsPRFromSet(userId, exerciseId, set),
                checkEstimated1RMPRFromSet(userId, exerciseId, set)
        ).filter(Objects::nonNull);
    }

    /**
     * Check for personal records in a single exercise (legacy method for backward compatibility)
     */
    private Flux<PersonalRecord> checkForPRs(Long userId, WorkoutExercise exercise, Workout workout) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(exercise.getSets())
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .flatMap(set -> checkSetForPRs(userId, exercise.getExerciseId(), set))
                .distinct(); // Avoid duplicate PRs
    }

    /**
     * FIXED: Check for maximum weight PR from a set
     */
    private Mono<PersonalRecord> checkMaxWeightPRFromSet(Long userId, String exerciseId, WorkoutSet set) {
        BigDecimal currentWeight = set.getWeightKg();

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exerciseId, "MAX_WEIGHT")
                .switchIfEmpty(Mono.just(createEmptyPR())) // Create empty PR if none exists
                .flatMap(existingPR -> {
                    BigDecimal existingWeight = existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO;

                    if (currentWeight.compareTo(existingWeight) > 0) {
                        return createPersonalRecordFromSet(userId, exerciseId, set, "MAX_WEIGHT",
                                currentWeight, existingWeight);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Check for maximum volume PR from a set
     */
    private Mono<PersonalRecord> checkMaxVolumePRFromSet(Long userId, String exerciseId, WorkoutSet set) {
        BigDecimal currentVolume = set.getVolume(); // This is already calculated in WorkoutSet

        if (currentVolume == null || currentVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.empty();
        }

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exerciseId, "MAX_VOLUME")
                .switchIfEmpty(Mono.just(createEmptyPR()))
                .flatMap(existingPR -> {
                    BigDecimal existingVolume = existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO;

                    if (currentVolume.compareTo(existingVolume) > 0) {
                        return createPersonalRecordFromSet(userId, exerciseId, set, "MAX_VOLUME",
                                currentVolume, existingVolume);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Check for maximum reps PR from a set
     */
    private Mono<PersonalRecord> checkMaxRepsPRFromSet(Long userId, String exerciseId, WorkoutSet set) {
        BigDecimal setWeight = set.getWeightKg();
        Integer setReps = set.getReps();

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exerciseId, "MAX_REPS")
                .switchIfEmpty(Mono.just(createEmptyPR()))
                .flatMap(existingPR -> {
                    // Only count as PR if weight is same or higher and reps are more
                    BigDecimal existingWeight = existingPR.getWeight() != null ? existingPR.getWeight() : BigDecimal.ZERO;
                    Integer existingReps = existingPR.getReps() != null ? existingPR.getReps() : 0;

                    boolean isNewPR = existingPR.getValue() == null ||
                            (setWeight.compareTo(existingWeight) >= 0 && setReps > existingReps);

                    if (isNewPR) {
                        return createPersonalRecordFromSet(userId, exerciseId, set, "MAX_REPS",
                                BigDecimal.valueOf(setReps),
                                existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Check for estimated 1RM PR from a set
     */
    private Mono<PersonalRecord> checkEstimated1RMPRFromSet(Long userId, String exerciseId, WorkoutSet set) {
        BigDecimal estimated1RM = calculate1RM(set.getWeightKg(), set.getReps());

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exerciseId, "ESTIMATED_1RM")
                .switchIfEmpty(Mono.just(createEmptyPR()))
                .flatMap(existingPR -> {
                    BigDecimal existing1RM = existingPR.getValue() != null ? existingPR.getValue() : BigDecimal.ZERO;

                    if (estimated1RM.compareTo(existing1RM) > 0) {
                        return createPersonalRecordFromSet(userId, exerciseId, set, "ESTIMATED_1RM",
                                estimated1RM, existing1RM);
                    }
                    return Mono.empty();
                });
    }

    /**
     * FIXED: Create a new personal record from a set
     */
    private Mono<PersonalRecord> createPersonalRecordFromSet(Long userId, String exerciseId, WorkoutSet set,
                                                             String recordType, BigDecimal value, BigDecimal previousValue) {

        PersonalRecord pr = PersonalRecord.builder()
                .userId(userId)
                .exerciseId(exerciseId)
                .exerciseName(getExerciseName(exerciseId))
                .recordType(recordType)
                .value(value)
                .weight(set.getWeightKg())
                .reps(set.getReps())
                .sets(1) // This is for a single set
                .achievedDate(LocalDateTime.now())
                .previousRecord(previousValue)
                .build();

        // Calculate improvement percentage
        if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal improvement = value.subtract(previousValue)
                    .divide(previousValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            pr.setImprovementPercentage(improvement.doubleValue());
        }

        log.info("New {} PR for user {} exercise {}: {} (previous: {})",
                recordType, userId, exerciseId, value, previousValue);

        return personalRecordRepository.save(pr);
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
     * Get exercise name - placeholder implementation
     */
    private String getExerciseName(String exerciseId) {
        // This would typically fetch from Exercise service
        return "Exercise " + exerciseId.substring(0, Math.min(8, exerciseId.length()));
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