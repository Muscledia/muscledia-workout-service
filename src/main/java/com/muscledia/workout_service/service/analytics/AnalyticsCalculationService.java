package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.ExerciseAnalytics;
import com.muscledia.workout_service.model.analytics.WorkoutAnalytics;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsCalculationService {

    /**
     * Calculate comprehensive workout analytics from workout data
     */
    public Mono<WorkoutAnalytics> calculateWorkoutAnalytics(Long userId, String period,
            LocalDateTime periodStart, LocalDateTime periodEnd,
            List<Workout> workouts) {
        log.info("Calculating analytics for {} workouts", workouts.size());

        WorkoutAnalytics analytics = new WorkoutAnalytics();
        analytics.setUserId(userId);
        analytics.setAnalysisPeriod(period);
        analytics.setPeriodStart(periodStart);
        analytics.setPeriodEnd(periodEnd);

        // Basic workout statistics
        calculateBasicStats(analytics, workouts, periodStart, periodEnd);

        // Exercise-specific analytics
        List<ExerciseAnalytics> exerciseAnalytics = calculateExerciseAnalytics(workouts, periodStart, periodEnd);
        analytics.setExerciseAnalytics(exerciseAnalytics);

        // Progress metrics
        calculateProgressMetrics(analytics, workouts, exerciseAnalytics);

        return Mono.just(analytics);
    }

    /**
     * Calculate basic workout statistics
     */
    private void calculateBasicStats(WorkoutAnalytics analytics, List<Workout> workouts,
            LocalDateTime periodStart, LocalDateTime periodEnd) {
        analytics.setTotalWorkouts(workouts.size());

        if (workouts.isEmpty()) {
            setZeroStats(analytics);
            return;
        }

        // Duration statistics
        int totalDuration = workouts.stream()
                .mapToInt(Workout::getDurationMinutes)
                .sum();
        analytics.setTotalDurationMinutes(totalDuration);
        analytics.setAverageDurationMinutes((double) totalDuration / workouts.size());

        // Volume statistics
        BigDecimal totalVolume = workouts.stream()
                .map(Workout::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        analytics.setTotalVolume(totalVolume);
        analytics.setAverageVolumePerWorkout(
                totalVolume.divide(BigDecimal.valueOf(workouts.size()), 2, RoundingMode.HALF_UP));

        // Frequency calculation
        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        double weeksInPeriod = periodDays / 7.0;
        analytics.setWorkoutFrequencyPerWeek(workouts.size() / weeksInPeriod);
    }

    /**
     * Calculate exercise-specific analytics
     */
    private List<ExerciseAnalytics> calculateExerciseAnalytics(List<Workout> workouts,
            LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Group exercises by exercise ID
        Map<String, List<WorkoutExercise>> exerciseMap = new HashMap<>();
        Map<String, String> exerciseNames = new HashMap<>();

        for (Workout workout : workouts) {
            for (WorkoutExercise exercise : workout.getExercises()) {
                exerciseMap.computeIfAbsent(exercise.getExerciseId(), k -> new ArrayList<>()).add(exercise);
                exerciseNames.put(exercise.getExerciseId(), getExerciseName(exercise.getExerciseId()));
            }
        }

        return exerciseMap.entrySet().stream()
                .map(entry -> calculateSingleExerciseAnalytics(
                        entry.getKey(), exerciseNames.get(entry.getKey()), entry.getValue(), periodStart, periodEnd))
                .collect(Collectors.toList());
    }

    /**
     * Calculate analytics for a single exercise
     */
    private ExerciseAnalytics calculateSingleExerciseAnalytics(String exerciseId, String exerciseName,
            List<WorkoutExercise> exercises,
            LocalDateTime periodStart, LocalDateTime periodEnd) {
        ExerciseAnalytics analytics = new ExerciseAnalytics();
        analytics.setExerciseId(exerciseId);
        analytics.setExerciseName(exerciseName);

        // Volume calculations
        BigDecimal totalVolume = exercises.stream()
                .map(this::calculateExerciseVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        analytics.setTotalVolume(totalVolume);

        // Count unique sessions
        int sessionsCount = (int) exercises.stream()
                .collect(Collectors.groupingBy(e -> e.getExerciseId())) // This would need workout date grouping
                .size();
        analytics.setSessionsCount(sessionsCount);

        if (sessionsCount > 0) {
            analytics.setAverageVolumePerSession(
                    totalVolume.divide(BigDecimal.valueOf(sessionsCount), 2, RoundingMode.HALF_UP));
        }

        // Weight statistics
        BigDecimal maxWeight = exercises.stream()
                .map(WorkoutExercise::getWeight)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        analytics.setMaxWeight(maxWeight);

        BigDecimal avgWeight = exercises.stream()
                .map(WorkoutExercise::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(exercises.size()), 2, RoundingMode.HALF_UP);
        analytics.setAverageWeight(avgWeight);

        // 1RM estimation (using Epley formula: weight * (1 + reps/30))
        BigDecimal estimated1RM = exercises.stream()
                .map(this::calculate1RM)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        analytics.setEstimated1RM(estimated1RM);

        // Set and rep statistics
        analytics.setTotalSets(exercises.stream().mapToInt(WorkoutExercise::getSets).sum());
        analytics.setTotalReps(exercises.stream().mapToInt(WorkoutExercise::getReps).sum());
        analytics.setAverageSetsPerSession((double) analytics.getTotalSets() / sessionsCount);
        analytics.setAverageRepsPerSet((double) analytics.getTotalReps() / analytics.getTotalSets());

        // Frequency calculation
        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        double weeksInPeriod = periodDays / 7.0;
        analytics.setFrequencyPerWeek(sessionsCount / weeksInPeriod);

        // Trends (simplified - would need historical data for proper calculation)
        analytics.setVolumeTrend(calculateTrend(exercises, "VOLUME"));
        analytics.setStrengthTrend(calculateTrend(exercises, "STRENGTH"));

        // Progress score (0-100 based on improvement)
        analytics.setProgressScore(calculateProgressScore(exercises));

        return analytics;
    }

    /**
     * Calculate progress metrics for overall analytics
     */
    private void calculateProgressMetrics(WorkoutAnalytics analytics, List<Workout> workouts,
            List<ExerciseAnalytics> exerciseAnalytics) {
        // Volume trend analysis
        analytics.setVolumeTrend(calculateOverallVolumeTrend(workouts));
        analytics.setVolumeChangePercentage(calculateVolumeChangePercentage(workouts));

        // Strength progression score
        double avgProgressScore = exerciseAnalytics.stream()
                .mapToDouble(ExerciseAnalytics::getProgressScore)
                .filter(score -> score > 0)
                .average()
                .orElse(0.0);
        analytics.setStrengthProgressionScore(avgProgressScore);

        // PR count (would be calculated from PersonalRecord service)
        analytics.setNewPRsCount(0); // Placeholder
    }

    /**
     * Generate insights and recommendations
     */
    public Mono<List<String>> generateInsights(WorkoutAnalytics analytics) {
        List<String> insights = new ArrayList<>();

        // Frequency insights
        if (analytics.getWorkoutFrequencyPerWeek() < 2) {
            insights.add("Consider increasing workout frequency to 3-4 times per week for better progress");
        } else if (analytics.getWorkoutFrequencyPerWeek() > 6) {
            insights.add("High workout frequency detected - ensure adequate recovery time");
        }

        // Volume insights
        if (analytics.getVolumeChangePercentage() != null) {
            if (analytics.getVolumeChangePercentage() > 20) {
                insights.add("Excellent volume progression! You're getting stronger");
            } else if (analytics.getVolumeChangePercentage() < -10) {
                insights.add("Volume has decreased - consider reviewing your training program");
            }
        }

        // Duration insights
        if (analytics.getAverageDurationMinutes() > 120) {
            insights.add("Long workout sessions - consider optimizing for efficiency");
        } else if (analytics.getAverageDurationMinutes() < 30) {
            insights.add("Short workouts can be effective - ensure adequate intensity");
        }

        // Exercise-specific insights
        long stagnantExercises = analytics.getExerciseAnalytics().stream()
                .filter(ex -> "STABLE".equals(ex.getStrengthTrend()) && ex.getSessionsCount() > 10)
                .count();

        if (stagnantExercises > 0) {
            insights.add(String.format("Consider varying %d exercises that have plateaued", stagnantExercises));
        }

        return Mono.just(insights);
    }

    // Helper methods

    private void setZeroStats(WorkoutAnalytics analytics) {
        analytics.setTotalDurationMinutes(0);
        analytics.setAverageDurationMinutes(0.0);
        analytics.setTotalVolume(BigDecimal.ZERO);
        analytics.setAverageVolumePerWorkout(BigDecimal.ZERO);
        analytics.setWorkoutFrequencyPerWeek(0.0);
        analytics.setExerciseAnalytics(new ArrayList<>());
    }

    private BigDecimal calculateExerciseVolume(WorkoutExercise exercise) {
        return exercise.getWeight()
                .multiply(BigDecimal.valueOf(exercise.getSets()))
                .multiply(BigDecimal.valueOf(exercise.getReps()));
    }

    private BigDecimal calculate1RM(WorkoutExercise exercise) {
        // Epley formula: weight * (1 + reps/30)
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(exercise.getReps()).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
        return exercise.getWeight().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private String calculateTrend(List<WorkoutExercise> exercises, String type) {
        // Simplified trend calculation - would need proper time-series analysis
        if (exercises.size() < 3)
            return "INSUFFICIENT_DATA";

        // For now, return a random trend - implement proper calculation
        return "STABLE"; // Placeholder
    }

    private String calculateOverallVolumeTrend(List<Workout> workouts) {
        if (workouts.size() < 2)
            return "INSUFFICIENT_DATA";

        // Compare first half vs second half of period
        int midPoint = workouts.size() / 2;
        BigDecimal firstHalfVolume = workouts.subList(0, midPoint).stream()
                .map(Workout::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal secondHalfVolume = workouts.subList(midPoint, workouts.size()).stream()
                .map(Workout::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (secondHalfVolume.compareTo(firstHalfVolume.multiply(BigDecimal.valueOf(1.1))) > 0) {
            return "INCREASING";
        } else if (secondHalfVolume.compareTo(firstHalfVolume.multiply(BigDecimal.valueOf(0.9))) < 0) {
            return "DECREASING";
        } else {
            return "STABLE";
        }
    }

    private Double calculateVolumeChangePercentage(List<Workout> workouts) {
        if (workouts.size() < 2)
            return 0.0;

        // Compare first vs last workout volumes
        BigDecimal firstVolume = workouts.get(workouts.size() - 1).getTotalVolume();
        BigDecimal lastVolume = workouts.get(0).getTotalVolume();

        if (firstVolume == null || lastVolume == null || firstVolume.equals(BigDecimal.ZERO)) {
            return 0.0;
        }

        return lastVolume.subtract(firstVolume)
                .divide(firstVolume, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private Double calculateProgressScore(List<WorkoutExercise> exercises) {
        // Simplified progress score calculation
        if (exercises.size() < 3)
            return 50.0; // Neutral score for insufficient data

        // Calculate based on weight progression, volume increase, etc.
        // This is a placeholder - implement proper scoring algorithm
        return 75.0;
    }

    private String getExerciseName(String exerciseId) {
        // This would typically fetch from Exercise repository
        // Placeholder implementation
        return "Exercise " + exerciseId.substring(0, Math.min(8, exerciseId.length()));
    }
}