package com.muscledia.workout_service.repository.analytics;

import com.muscledia.workout_service.model.analytics.PersonalRecord;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Repository
public interface PersonalRecordRepository extends ReactiveMongoRepository<PersonalRecord, String> {

    /**
     * Find all PRs for a user, ordered by achievement date (most recent first)
     */
    Flux<PersonalRecord> findByUserIdOrderByAchievedDateDesc(Long userId);

    /**
     * Find PRs for a specific exercise, ordered by achievement date
     */
    Flux<PersonalRecord> findByUserIdAndExerciseIdOrderByAchievedDateDesc(Long userId, String exerciseId);

    /**
     * FIXED: Find specific type of PR for an exercise (should return most recent one)
     */
    @Query(value = "{ 'userId': ?0, 'exerciseId': ?1, 'recordType': ?2 }",
            sort = "{ 'achievedDate': -1 }")
    Mono<PersonalRecord> findByUserIdAndExerciseIdAndRecordType(Long userId, String exerciseId, String recordType);

    // DATE-BASED QUERIES

    /**
     * Find recent PRs (within specified date)
     */
    Flux<PersonalRecord> findByUserIdAndAchievedDateGreaterThanOrderByAchievedDateDesc(
            Long userId, LocalDateTime cutoffDate);

    /**
     * Find PRs achieved in a date range
     */
    Flux<PersonalRecord> findByUserIdAndAchievedDateBetweenOrderByAchievedDateDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    // RECORD TYPE QUERIES

    /**
     * Find PRs by record type, ordered by value (highest first)
     */
    @Query(value = "{ 'userId': ?0, 'recordType': ?1 }", sort = "{ 'value': -1 }")
    Flux<PersonalRecord> findByUserIdAndRecordTypeOrderByValueDesc(Long userId, String recordType);

    /**
     * Find all weight records for a user
     */
    @Query("{ 'userId': ?0, 'recordType': { $in: ['MAX_WEIGHT', 'ESTIMATED_1RM'] } }")
    Flux<PersonalRecord> findWeightRecordsByUserId(Long userId);

    // VALUE COMPARISON QUERIES

    /**
     * Check if a new value would be a PR (find existing records >= the value)
     */
    @Query("{ 'userId': ?0, 'exerciseId': ?1, 'recordType': ?2, 'value': { $gte: ?3 } }")
    Flux<PersonalRecord> findExistingRecordsGreaterThanOrEqual(
            Long userId, String exerciseId, String recordType, BigDecimal value);

    /**
     * Find PRs above a certain value threshold
     */
    @Query("{ 'userId': ?0, 'recordType': ?1, 'value': { $gte: ?2 } }")
    Flux<PersonalRecord> findByUserIdAndRecordTypeAndValueGreaterThanEqual(
            Long userId, String recordType, BigDecimal minValue);

    // DISTINCT AND AGGREGATION QUERIES

    /**
     * Find all exercise IDs with PRs for a user
     */
    @Query(value = "{ 'userId': ?0 }", fields = "{ 'exerciseId': 1, 'exerciseName': 1 }")
    Flux<PersonalRecord> findDistinctExercisesByUserId(Long userId);

    /**
     * Find latest PR for each exercise for a user
     */
    Flux<PersonalRecord> findLatestPRsByUserId(Long userId);

    // COUNT QUERIES

    /**
     * Count total PRs for a user
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Count PRs achieved in a period
     */
    Mono<Long> countByUserIdAndAchievedDateBetween(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count PRs by record type
     */
    Mono<Long> countByUserIdAndRecordType(Long userId, String recordType);

    /**
     * Count PRs for a specific exercise
     */
    Mono<Long> countByUserIdAndExerciseId(Long userId, String exerciseId);

    // WORKOUT-BASED QUERIES

    /**
     * Find PRs achieved in a specific workout
     */
    Flux<PersonalRecord> findByUserIdAndWorkoutIdOrderByAchievedDateDesc(Long userId, String workoutId);

    /**
     * Find PRs with improvement over previous record
     */
    @Query("{ 'userId': ?0, 'improvementPercentage': { $gt: 0 } }")
    Flux<PersonalRecord> findPRsWithImprovement(Long userId);

    // VERIFICATION QUERIES

    /**
     * Find verified PRs only
     */
    Flux<PersonalRecord> findByUserIdAndVerifiedTrueOrderByAchievedDateDesc(Long userId);

    /**
     * Find unverified PRs
     */
    Flux<PersonalRecord> findByUserIdAndVerifiedFalseOrderByAchievedDateDesc(Long userId);

    // TOP PERFORMANCE QUERIES

    /**
     * Find top N PRs by value for a record type
     */
    @Query(value = "{ 'userId': ?0, 'recordType': ?1 }", sort = "{ 'value': -1 }")
    Flux<PersonalRecord> findTopPRsByRecordType(Long userId, String recordType);

    /**
     * Find recent improvements (PRs with high improvement percentage)
     */
    @Query(value = "{ 'userId': ?0, 'improvementPercentage': { $gte: ?1 } }",
            sort = "{ 'achievedDate': -1 }")
    Flux<PersonalRecord> findRecentImprovements(Long userId, Double minImprovementPercentage);

    // ADMIN/SYSTEM QUERIES

    /**
     * Find all PRs across all users (admin only)
     */
    @Query(value = "{}", sort = "{ 'achievedDate': -1 }")
    Flux<PersonalRecord> findAllOrderByAchievedDateDesc();

    /**
     * Find PRs for multiple users
     */
    @Query("{ 'userId': { $in: ?0 } }")
    Flux<PersonalRecord> findByUserIdIn(java.util.List<Long> userIds);

    Flux<PersonalRecord> findByUserId(Long userId);
}