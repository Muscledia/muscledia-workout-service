package com.muscledia.workout_service.repository.analytics;

import com.muscledia.workout_service.model.analytics.ProgressSnapshot;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
public interface ProgressSnapshotRepository extends ReactiveMongoRepository<ProgressSnapshot, String> {

    /**
     * Find snapshots for a user and exercise
     */
    Flux<ProgressSnapshot> findByUserIdAndExerciseIdOrderBySnapshotDateDesc(Long userId, String exerciseId);

    /**
     * Find snapshots within a date range
     */
    Flux<ProgressSnapshot> findByUserIdAndExerciseIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
            Long userId, String exerciseId, LocalDate startDate, LocalDate endDate);

    /**
     * Find the most recent snapshot for an exercise
     */
    Mono<ProgressSnapshot> findTopByUserIdAndExerciseIdOrderBySnapshotDateDesc(Long userId, String exerciseId);

    /**
     * Find all snapshots for a user on a specific date
     */
    Flux<ProgressSnapshot> findByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

    /**
     * Find snapshots for multiple exercises
     */
    Flux<ProgressSnapshot> findByUserIdAndExerciseIdInOrderBySnapshotDateDesc(Long userId,
            java.util.List<String> exerciseIds);

    /**
     * Find recent snapshots (last N days)
     */
    Flux<ProgressSnapshot> findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateDesc(Long userId,
            LocalDate cutoffDate);

    /**
     * Check if snapshot exists for a specific date and exercise
     */
    Mono<Boolean> existsByUserIdAndExerciseIdAndSnapshotDate(Long userId, String exerciseId, LocalDate snapshotDate);

    /**
     * Delete old snapshots for cleanup
     */
    @Query("{ 'user_id': ?0, 'snapshot_date': { $lt: ?1 } }")
    Flux<ProgressSnapshot> findByUserIdAndSnapshotDateBefore(Long userId, LocalDate cutoffDate);

    /**
     * Find snapshots with specific trend patterns
     */
    @Query("{ 'user_id': ?0, 'weight_trend_30_days': ?1 }")
    Flux<ProgressSnapshot> findByUserIdAndWeightTrend30Days(Long userId, String trend);

    /**
     * Get exercise IDs with progress data
     */
    @Query(value = "{ 'user_id': ?0 }", fields = "{ 'exercise_id': 1, 'exercise_name': 1 }")
    Flux<ProgressSnapshot> findDistinctExercisesByUserId(Long userId);
}