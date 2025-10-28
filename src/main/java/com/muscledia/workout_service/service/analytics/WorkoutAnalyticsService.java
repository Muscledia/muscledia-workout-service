package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.dto.response.analytics.WorkoutAnalyticsResponse;
import com.muscledia.workout_service.model.analytics.WorkoutAnalytics;
import com.muscledia.workout_service.repository.analytics.WorkoutAnalyticsRepository;
import com.muscledia.workout_service.service.WorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutAnalyticsService {

    private final WorkoutAnalyticsRepository analyticsRepository;
    private final WorkoutService workoutService;
    private final AnalyticsCalculationService calculationService;
    private final PersonalRecordService personalRecordService;
    private final ProgressTrackingService progressTrackingService;
    private final AnalyticsMapperService mapperService;

    /**
     * Generate comprehensive analytics for a user and period
     */
    public Mono<WorkoutAnalyticsResponse> generateAnalytics(Long userId, String period) {
        log.info("Generating analytics for user {} for period {}", userId, period);

        return getOrCreateAnalytics(userId, period)
                .flatMap(analytics -> enhanceWithRealtimeData(analytics, userId))
                .map(mapperService::toResponse)
                .doOnSuccess(response -> log.info("Generated analytics with {} exercises for user {}",
                        response.getExerciseAnalytics().size(), userId))
                .doOnError(
                        error -> log.error("Error generating analytics for user {}: {}", userId, error.getMessage()));
    }

    /**
     * Get existing analytics or create new ones
     */
    private Mono<WorkoutAnalytics> getOrCreateAnalytics(Long userId, String period) {
        LocalDateTime[] periodBounds = calculatePeriodBounds(period);
        LocalDateTime periodStart = periodBounds[0];
        LocalDateTime periodEnd = periodBounds[1];

        return analyticsRepository.findTopByUserIdAndAnalysisPeriodOrderByCreatedAtDesc(userId, period)
                .filter(analytics -> isAnalyticsCurrent(analytics, periodEnd))
                .switchIfEmpty(calculateAndSaveAnalytics(userId, period, periodStart, periodEnd));
    }

    /**
     * Calculate and save new analytics
     */
    private Mono<WorkoutAnalytics> calculateAndSaveAnalytics(Long userId, String period,
            LocalDateTime periodStart, LocalDateTime periodEnd) {
        log.info("Calculating new analytics for user {} period {} from {} to {}",
                userId, period, periodStart, periodEnd);

        return workoutService.getUserWorkouts(userId, periodStart.toLocalDate().atStartOfDay(), periodEnd.toLocalDate().atStartOfDay())
                .collectList()
                .flatMap(workouts -> calculationService.calculateWorkoutAnalytics(
                        userId, period, periodStart, periodEnd, workouts))
                .flatMap(analyticsRepository::save)
                .doOnSuccess(analytics -> log.info("Saved new analytics for user {} with {} workouts",
                        userId, analytics.getTotalWorkouts()));
    }

    /**
     * Enhance analytics with real-time data
     */
    private Mono<WorkoutAnalytics> enhanceWithRealtimeData(WorkoutAnalytics analytics, Long userId) {
        return Mono.zip(
                personalRecordService.getRecentPRs(userId, 30),
                progressTrackingService.getLatestProgressData(userId),
                calculationService.generateInsights(analytics)).map(tuple -> {
                    analytics.setRecentPRs(tuple.getT1());
                    // Enhance with progress data and insights
                    return analytics;
                });
    }

    /**
     * Get analytics for multiple periods (dashboard view)
     */
    public Flux<WorkoutAnalyticsResponse> getDashboardAnalytics(Long userId) {
        return Flux.fromArray(new String[] { "WEEKLY", "MONTHLY", "QUARTERLY" })
                .flatMap(period -> generateAnalytics(userId, period))
                .doOnComplete(() -> log.info("Generated dashboard analytics for user {}", userId));
    }

    /**
     * Get historical analytics for trend analysis
     */
    public Flux<WorkoutAnalyticsResponse> getHistoricalAnalytics(Long userId, String period, int periodsBack) {
        return Flux.range(0, periodsBack)
                .map(offset -> calculateHistoricalPeriod(period, offset))
                .flatMap(historicalPeriod -> {
                    LocalDateTime[] bounds = calculatePeriodBounds(historicalPeriod);
                    return analyticsRepository
                            .findByUserIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartDesc(
                                    userId, bounds[0], bounds[1])
                            .next()
                            .switchIfEmpty(calculateAndSaveAnalytics(userId, period, bounds[0], bounds[1]));
                })
                .map(mapperService::toResponse);
    }

    /**
     * Refresh analytics for a user (force recalculation)
     */
    public Mono<Void> refreshAnalytics(Long userId) {
        log.info("Refreshing all analytics for user {}", userId);

        return analyticsRepository.findByUserIdOrderByPeriodStartDesc(userId)
                .flatMap(analytics -> {
                    // Recalculate each analytics period
                    return calculateAndSaveAnalytics(userId, analytics.getAnalysisPeriod(),
                            analytics.getPeriodStart(), analytics.getPeriodEnd());
                })
                .then()
                .doOnSuccess(v -> log.info("Refreshed analytics for user {}", userId));
    }

    /**
     * Schedule analytics cleanup (remove old analytics)
     */
    public Mono<Void> cleanupOldAnalytics(Long userId, int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);

        return analyticsRepository.findByUserIdAndCreatedAtBefore(userId, cutoffDate)
                .flatMap(analyticsRepository::delete)
                .then()
                .doOnSuccess(v -> log.info("Cleaned up old analytics for user {} before {}", userId, cutoffDate));
    }

    // Helper methods

    private LocalDateTime[] calculatePeriodBounds(String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start, end;

        end = switch (period.toUpperCase()) {
            case "WEEKLY" -> {
                start = now.minusWeeks(1).truncatedTo(ChronoUnit.DAYS);
                yield now.truncatedTo(ChronoUnit.DAYS);
            }
            case "MONTHLY" -> {
                start = now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
                yield now.truncatedTo(ChronoUnit.DAYS);
            }
            case "QUARTERLY" -> {
                start = now.minusMonths(3).truncatedTo(ChronoUnit.DAYS);
                yield now.truncatedTo(ChronoUnit.DAYS);
            }
            case "YEARLY" -> {
                start = now.minusYears(1).truncatedTo(ChronoUnit.DAYS);
                yield now.truncatedTo(ChronoUnit.DAYS);
            }
            case "ALL_TIME" -> {
                start = LocalDateTime.of(2020, 1, 1, 0, 0); // Arbitrary start date
                yield now.truncatedTo(ChronoUnit.DAYS);
            }
            default -> throw new IllegalArgumentException("Invalid period: " + period);
        };

        return new LocalDateTime[] { start, end };
    }

    private boolean isAnalyticsCurrent(WorkoutAnalytics analytics, LocalDateTime periodEnd) {
        // Consider analytics current if calculated within last 6 hours and period end
        // is recent
        return analytics.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(6)) &&
                ChronoUnit.HOURS.between(periodEnd, LocalDateTime.now()) < 24;
    }

    private String calculateHistoricalPeriod(String period, int offset) {
        // This would calculate historical periods (e.g., "MONTHLY-2" for 2 months ago)
        return period + (offset > 0 ? "-" + offset : "");
    }
}