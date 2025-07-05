package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.dto.response.analytics.ProgressTrackingResponse;
import com.muscledia.workout_service.model.analytics.ProgressSnapshot;
import com.muscledia.workout_service.repository.analytics.ProgressSnapshotRepository;
import com.muscledia.workout_service.service.WorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressTrackingService {

    private final ProgressSnapshotRepository snapshotRepository;
    private final WorkoutService workoutService;

    /**
     * Get progress tracking data for an exercise
     */
    public Mono<ProgressTrackingResponse> getExerciseProgress(Long userId, String exerciseId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);
        LocalDate endDate = LocalDate.now();

        return snapshotRepository.findByUserIdAndExerciseIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
                userId, exerciseId, startDate, endDate)
                .collectList()
                .map(snapshots -> buildProgressResponse(exerciseId, snapshots, days))
                .doOnSuccess(response -> log.info("Generated progress tracking for exercise {} over {} days",
                        exerciseId, days));
    }

    /**
     * Get latest progress data for all exercises
     */
    public Mono<List<ProgressSnapshot>> getLatestProgressData(Long userId) {
        return snapshotRepository.findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateDesc(
                userId, LocalDate.now().minusDays(7))
                .collectList();
    }

    /**
     * Create daily progress snapshots (scheduled task)
     */
    public Mono<Void> createDailySnapshots(Long userId) {
        // This would be called by a scheduler to create daily progress snapshots
        // Implementation would calculate current metrics and save snapshots
        log.info("Creating daily progress snapshots for user {}", userId);
        return Mono.empty(); // Placeholder
    }

    private ProgressTrackingResponse buildProgressResponse(String exerciseId, List<ProgressSnapshot> snapshots,
            int days) {
        ProgressTrackingResponse response = new ProgressTrackingResponse();
        response.setExerciseId(exerciseId);
        response.setTrackingPeriodDays(days);

        if (snapshots.isEmpty()) {
            return response;
        }

        ProgressSnapshot latest = snapshots.get(0);
        response.setExerciseName(latest.getExerciseName());
        response.setCurrentMaxWeight(latest.getCurrentMaxWeight());
        response.setCurrentEstimated1RM(latest.getCurrentEstimated1RM());
        response.setCurrentAverageVolume(latest.getTotalVolumeLast30Days());

        // Set trends from latest snapshot
        response.setWeightTrend7Days(latest.getWeightTrend7Days());
        response.setWeightTrend30Days(latest.getWeightTrend30Days());
        response.setVolumeTrend7Days(latest.getVolumeTrend7Days());
        response.setVolumeTrend30Days(latest.getVolumeTrend30Days());

        // Calculate improvements
        if (snapshots.size() > 1) {
            ProgressSnapshot oldest = snapshots.get(snapshots.size() - 1);
            response.setWeightImprovementPercentage(calculateImprovement(
                    oldest.getCurrentMaxWeight(), latest.getCurrentMaxWeight()));
            response.setVolumeImprovementPercentage(calculateImprovement(
                    oldest.getTotalVolumeLast30Days(), latest.getTotalVolumeLast30Days()));
            response.setOneRMImprovementPercentage(calculateImprovement(
                    oldest.getCurrentEstimated1RM(), latest.getCurrentEstimated1RM()));
        }

        // Build progress history
        List<ProgressTrackingResponse.ProgressDataPoint> history = snapshots.stream()
                .map(this::toDataPoint)
                .toList();
        response.setProgressHistory(history);

        // Simple predictions (would use more sophisticated algorithms in production)
        response.setPredicted1RMIn30Days(predictFutureValue(latest.getCurrentEstimated1RM(), 5.0));
        response.setPredictedVolumeIn30Days(predictFutureValue(latest.getTotalVolumeLast30Days(), 3.0));
        response.setPredictionConfidence(75.0); // Placeholder confidence

        return response;
    }

    private Double calculateImprovement(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null || oldValue.equals(BigDecimal.ZERO)) {
            return 0.0;
        }

        return newValue.subtract(oldValue)
                .divide(oldValue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private ProgressTrackingResponse.ProgressDataPoint toDataPoint(ProgressSnapshot snapshot) {
        ProgressTrackingResponse.ProgressDataPoint point = new ProgressTrackingResponse.ProgressDataPoint();
        point.setDate(snapshot.getSnapshotDate());
        point.setMaxWeight(snapshot.getCurrentMaxWeight());
        point.setAverageVolume(snapshot.getTotalVolumeLast30Days());
        point.setEstimated1RM(snapshot.getCurrentEstimated1RM());
        point.setWorkoutFrequency7Days(snapshot.getWorkoutFrequencyLast30Days());
        return point;
    }

    private BigDecimal predictFutureValue(BigDecimal currentValue, double growthPercentage) {
        if (currentValue == null)
            return BigDecimal.ZERO;

        BigDecimal growth = BigDecimal.valueOf(1 + growthPercentage / 100);
        return currentValue.multiply(growth).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}