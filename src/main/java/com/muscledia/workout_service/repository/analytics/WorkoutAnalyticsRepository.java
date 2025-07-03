package com.muscledia.workout_service.repository.analytics;

import com.muscledia.workout_service.model.analytics.WorkoutAnalytics;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface WorkoutAnalyticsRepository extends ReactiveMongoRepository<WorkoutAnalytics, String> {

    /**
     * Find analytics by user ID and analysis period
     */
    Flux<WorkoutAnalytics> findByUserIdAndAnalysisPeriodOrderByPeriodStartDesc(Long userId, String analysisPeriod);

    /**
     * Find the most recent analytics for a user and period
     */
    Mono<WorkoutAnalytics> findTopByUserIdAndAnalysisPeriodOrderByCreatedAtDesc(Long userId, String analysisPeriod);

    /**
     * Find analytics within a date range
     */
    Flux<WorkoutAnalytics> findByUserIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find all analytics for a user ordered by period
     */
    Flux<WorkoutAnalytics> findByUserIdOrderByPeriodStartDesc(Long userId);

    /**
     * Delete old analytics records (for cleanup)
     */
    @Query("{ 'user_id': ?0, 'created_at': { $lt: ?1 } }")
    Flux<WorkoutAnalytics> findByUserIdAndCreatedAtBefore(Long userId, LocalDateTime cutoffDate);

    /**
     * Check if analytics exist for a specific period
     */
    Mono<Boolean> existsByUserIdAndAnalysisPeriodAndPeriodStartAndPeriodEnd(
            Long userId, String analysisPeriod, LocalDateTime periodStart, LocalDateTime periodEnd);

    /**
     * Get analytics summary for dashboard
     */
    @Query("{ 'user_id': ?0, 'analysis_period': { $in: ['WEEKLY', 'MONTHLY'] } }")
    Flux<WorkoutAnalytics> findRecentAnalyticsByUserId(Long userId);
}