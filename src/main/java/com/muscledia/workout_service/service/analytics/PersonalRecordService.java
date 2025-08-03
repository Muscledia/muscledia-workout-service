package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.repository.analytics.PersonalRecordRepository;
import com.muscledia.workout_service.service.ExerciseService;
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
     * Check for personal records in a single exercise
     */
    private Flux<PersonalRecord> checkForPRs(Long userId, WorkoutExercise exercise, Workout workout) {
        return Flux.concat(
                checkMaxWeightPR(userId, exercise, workout),
                checkMaxVolumePR(userId, exercise, workout),
                checkMaxRepsPR(userId, exercise, workout),
                checkEstimated1RMPR(userId, exercise, workout)).filter(Objects::nonNull);
    }

    /**
     * Check for maximum weight PR
     */
    private Mono<PersonalRecord> checkMaxWeightPR(Long userId, WorkoutExercise exercise, Workout workout) {
        // Ensure exercise.getWeight() is not null before converting
        BigDecimal currentWeight = exercise.getWeight() != null ?
                BigDecimal.valueOf(exercise.getWeight()) : BigDecimal.ZERO;
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exercise.getExerciseId(), "MAX_WEIGHT")
                .switchIfEmpty(Mono.just(new PersonalRecord())) // Create empty PR if none exists
                .flatMap(existingPR -> {
                    // FIX: Convert existingPR.getValue() to BigDecimal for comparison
                    if (existingPR.getValue() == null || currentWeight.compareTo(BigDecimal.valueOf(existingPR.getValue().doubleValue())) > 0) {
                        return createPersonalRecord(userId, exercise, workout, "MAX_WEIGHT",
                                currentWeight, existingPR.getValue());
                    }
                    return Mono.empty();
                });
    }

    /**
     * Check for maximum volume PR (weight × sets × reps)
     */
    private Mono<PersonalRecord> checkMaxVolumePR(Long userId, WorkoutExercise exercise, Workout workout) {
        // FIX: Ensure exercise.getWeight() is converted to BigDecimal for multiplication
        BigDecimal currentWeight = exercise.getWeight() != null ?
                BigDecimal.valueOf(exercise.getWeight()) : BigDecimal.ZERO;

        BigDecimal currentVolume = currentWeight
                .multiply(BigDecimal.valueOf(exercise.getSets()))
                .multiply(BigDecimal.valueOf(exercise.getReps()));

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                userId, exercise.getExerciseId(), "MAX_VOLUME")
                .switchIfEmpty(Mono.just(new PersonalRecord()))
                .flatMap(existingPR -> {
                    if (existingPR.getValue() == null || currentVolume.compareTo(existingPR.getValue()) > 0) {
                        return createPersonalRecord(userId, exercise, workout, "MAX_VOLUME",
                                currentVolume, existingPR.getValue());
                    }
                    return Mono.empty();
                });
    }

    /**
     * Check for maximum reps PR (at same or higher weight)
     */
    private Mono<PersonalRecord> checkMaxRepsPR(Long userId, WorkoutExercise exercise, Workout workout) {
        
        BigDecimal exerciseWeight = exercise.getWeight() != null ?
                BigDecimal.valueOf(exercise.getWeight()) : BigDecimal.ZERO;

        int exerciseReps = exercise.getReps() != null ? exercise.getReps() : 0;
        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exercise.getExerciseId(), "MAX_REPS")
                .switchIfEmpty(Mono.just(new PersonalRecord()))
                .flatMap(existingPR -> {
                    // Only count as PR if weight is same or higher and reps are more
                    BigDecimal existingWeight = existingPR.getWeight() != null ? existingPR.getWeight() : BigDecimal.ZERO;
                    int existingReps = existingPR.getReps() != null ? existingPR.getReps() : 0;


                    boolean isNewPR = existingPR.getValue() == null ||
                            (exerciseWeight.compareTo(existingWeight) >= 0 &&
                                    exerciseReps > existingReps);

                    if (isNewPR) {
                        return createPersonalRecord(userId, exercise, workout, "MAX_REPS",
                                BigDecimal.valueOf(exerciseReps), existingPR.getValue());
                    }
                    return Mono.empty();
                });
    }

    /**
     * Check for estimated 1RM PR
     */
    private Mono<PersonalRecord> checkEstimated1RMPR(Long userId, WorkoutExercise exercise, Workout workout) {
        // FIX: Convert exercise.getWeight() to BigDecimal and exercise.getReps() to int
        BigDecimal estimated1RM = calculate1RM(
                exercise.getWeight() != null ? BigDecimal.valueOf(exercise.getWeight()) : BigDecimal.ZERO,
                exercise.getReps() != null ? exercise.getReps() : 0
        );

        return personalRecordRepository.findByUserIdAndExerciseIdAndRecordType(
                        userId, exercise.getExerciseId(), "ESTIMATED_1RM")
                .switchIfEmpty(Mono.just(new PersonalRecord()))
                .flatMap(existingPR -> {
                    // FIX: Convert existingPR.getValue() to BigDecimal for comparison
                    if (existingPR.getValue() == null || estimated1RM.compareTo(BigDecimal.valueOf(existingPR.getValue().doubleValue())) > 0) {
                        return createPersonalRecord(userId, exercise, workout, "ESTIMATED_1RM",
                                estimated1RM, existingPR.getValue());
                    }
                    return Mono.empty();
                });
    }

    /**
     * Create a new personal record
     */
    private Mono<PersonalRecord> createPersonalRecord(Long userId, WorkoutExercise exercise, Workout workout,
            String recordType, BigDecimal value, BigDecimal previousValue) {
        PersonalRecord pr = new PersonalRecord();
        pr.setUserId(userId);
        pr.setExerciseId(exercise.getExerciseId());
        pr.setExerciseName(getExerciseName(exercise.getExerciseId())); // Would fetch from exercise service
        pr.setRecordType(recordType);
        pr.setValue(value);
        // FIX: Convert exercise.getWeight() to BigDecimal before setting
        pr.setWeight(exercise.getWeight() != null ? BigDecimal.valueOf(exercise.getWeight()) : null);
        pr.setReps(exercise.getReps());
        pr.setSets(exercise.getSets());
        pr.setWorkoutId(workout.getId());
        pr.setAchievedDate(workout.getWorkoutDate());
        pr.setPreviousRecord(previousValue);

        if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal improvement = value.subtract(previousValue)
                    .divide(previousValue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            pr.setImprovementPercentage(improvement.doubleValue());
        }

        log.info("New {} PR for user {} exercise {}: {} (previous: {})",
                recordType, userId, exercise.getExerciseId(), value, previousValue);

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

    // Helper methods

    private BigDecimal calculate1RM(BigDecimal weight, int reps) {
        if (reps == 1)
            return weight;

        // Epley formula: weight * (1 + reps/30)
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
        return weight.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private String getExerciseName(String exerciseId) {
        // This would typically fetch from Exercise service
        // Placeholder implementation
        return "Exercise " + exerciseId.substring(0, Math.min(8, exerciseId.length()));
    }

    // Inner class for PR statistics
    public static class PRStatistics {
        // Getters and setters
        @Setter
        @Getter
        private Integer totalPRs;
        private Integer pRsLast30Days;
        private Integer pRsLast7Days;

        public Integer getPRsLast30Days() {
            return pRsLast30Days;
        }

        public void setPRsLast30Days(Integer pRsLast30Days) {
            this.pRsLast30Days = pRsLast30Days;
        }

        public Integer getPRsLast7Days() {
            return pRsLast7Days;
        }

        public void setPRsLast7Days(Integer pRsLast7Days) {
            this.pRsLast7Days = pRsLast7Days;
        }
    }
}