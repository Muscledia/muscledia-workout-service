package com.muscledia.workout_service.repository.analytics;

import com.muscledia.workout_service.model.analytics.PersonalRecord;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface PersonalRecordRepository extends ReactiveMongoRepository<PersonalRecord, String> {

    /**
     * Find all PRs for a user
     */
    Flux<PersonalRecord> findByUserIdOrderByAchievedDateDesc(Long userId);

    /**
     * Find PRs for a specific exercise
     */
    Flux<PersonalRecord> findByUserIdAndExerciseIdOrderByAchievedDateDesc(Long userId, String exerciseId);

    /**
     * Find specific type of PR for an exercise
     */
    Mono<PersonalRecord> findByUserIdAndExerciseIdAndRecordType(Long userId, String exerciseId, String recordType);

    /**
     * Find recent PRs (within specified days)
     */
    Flux<PersonalRecord> findByUserIdAndAchievedDateGreaterThanOrderByAchievedDateDesc(Long userId,
            LocalDateTime cutoffDate);

    /**
     * Find PRs achieved in a date range
     */
    Flux<PersonalRecord> findByUserIdAndAchievedDateBetweenOrderByAchievedDateDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find top PRs by record type
     */
    @Query("{ 'user_id': ?0, 'record_type': ?1 }")
    Flux<PersonalRecord> findByUserIdAndRecordTypeOrderByValueDesc(Long userId, String recordType);

    /**
     * Check if a new value would be a PR
     */
    @Query("{ 'user_id': ?0, 'exercise_id': ?1, 'record_type': ?2, 'value': { $gte: ?3 } }")
    Mono<PersonalRecord> findExistingRecordGreaterThanOrEqual(Long userId, String exerciseId, String recordType,
            BigDecimal value);

    /**
     * Find all exercise IDs with PRs for a user
     */
    @Query(value = "{ 'user_id': ?0 }", fields = "{ 'exercise_id': 1, 'exercise_name': 1 }")
    Flux<PersonalRecord> findDistinctExercisesByUserId(Long userId);

    /**
     * Count total PRs for a user
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Count PRs achieved in a period
     */
    Mono<Long> countByUserIdAndAchievedDateBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find latest PR for each exercise
     */
//    @Query("{ $match: { 'user_id': ?0 } }, { $sort: { 'achieved_date': -1 } }, { $group: { '_id': '$exercise_id', 'latest_pr': { $first: '$$ROOT' } } }")
//    Flux<PersonalRecord> findLatestPRsByUserId(Long userId);
}