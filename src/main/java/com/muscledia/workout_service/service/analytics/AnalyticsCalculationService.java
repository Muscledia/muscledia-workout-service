package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.ExerciseAnalytics;
import com.muscledia.workout_service.model.analytics.WorkoutAnalytics;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
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

        WorkoutAnalytics.WorkoutAnalyticsBuilder analyticsBuilder = WorkoutAnalytics.builder()
                .userId(userId)
                .analysisPeriod(period)
                .periodStart(periodStart)
                .periodEnd(periodEnd);

        if (workouts.isEmpty()) {
            return Mono.just(buildEmptyAnalytics(analyticsBuilder));
        }

        // 1. Basic Stats
        int totalDuration = workouts.stream().mapToInt(Workout::getDurationMinutes).sum();

        BigDecimal totalVolume = workouts.stream()
                .map(Workout::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long periodDays = Math.max(1, ChronoUnit.DAYS.between(periodStart, periodEnd));
        double weeksInPeriod = periodDays / 7.0;

        analyticsBuilder
                .totalWorkouts(workouts.size())
                .totalDurationMinutes(totalDuration)
                .averageDurationMinutes((double) totalDuration / workouts.size())
                .totalVolume(totalVolume)
                .averageVolumePerWorkout(
                        totalVolume.divide(BigDecimal.valueOf(workouts.size()), 2, RoundingMode.HALF_UP))
                .workoutFrequencyPerWeek(workouts.size() / Math.max(1, weeksInPeriod));

        // 2. Exercise Analytics
        List<ExerciseAnalytics> exerciseAnalytics = calculateExerciseAnalytics(workouts, periodStart, periodEnd);
        analyticsBuilder.exerciseAnalytics(exerciseAnalytics);

        // 3. Overall Trends
        analyticsBuilder
                .volumeTrend(calculateOverallVolumeTrend(workouts))
                .volumeChangePercentage(calculateVolumeChangePercentage(workouts))
                .strengthProgressionScore(calculateAverageProgressScore(exerciseAnalytics))
                .newPRsCount(countNewPRs(workouts));

        return Mono.just(analyticsBuilder.build());
    }

    private List<ExerciseAnalytics> calculateExerciseAnalytics(List<Workout> workouts,
                                                               LocalDateTime periodStart, LocalDateTime periodEnd) {
        Map<String, List<WorkoutExercise>> exerciseMap = new HashMap<>();
        Map<String, String> exerciseNames = new HashMap<>();

        // Group exercises by ID and capture names
        for (Workout workout : workouts) {
            if (workout.getExercises() != null) {
                for (WorkoutExercise exercise : workout.getExercises()) {
                    exerciseMap.computeIfAbsent(exercise.getExerciseId(), k -> new ArrayList<>()).add(exercise);
                    // Update name cache if not present
                    exerciseNames.putIfAbsent(exercise.getExerciseId(), exercise.getExerciseName());
                }
            }
        }

        return exerciseMap.entrySet().stream()
                .map(entry -> calculateSingleExerciseAnalytics(
                        entry.getKey(),
                        exerciseNames.getOrDefault(entry.getKey(), "Unknown Exercise"),
                        entry.getValue(),
                        periodStart,
                        periodEnd))
                .collect(Collectors.toList());
    }

    private ExerciseAnalytics calculateSingleExerciseAnalytics(String exerciseId, String exerciseName,
                                                               List<WorkoutExercise> exercises,
                                                               LocalDateTime periodStart, LocalDateTime periodEnd) {

        BigDecimal totalVolume = exercises.stream()
                .map(WorkoutExercise::getTotalVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int sessionsCount = exercises.size();

        BigDecimal maxWeight = exercises.stream()
                .map(WorkoutExercise::getMaxWeight)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // Sort by date (implicitly by list order if sorted in service) for trends
        // Assuming 'exercises' list is chronological

        // 1RM Calculation
        BigDecimal estimated1RM = exercises.stream()
                .filter(ex -> ex.getSets() != null && !ex.getSets().isEmpty())
                .map(this::calculateExercise1RM)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int totalSets = exercises.stream()
                .mapToInt(ex -> ex.getSets() != null ? ex.getSets().size() : 0)
                .sum();

        long periodDays = Math.max(1, ChronoUnit.DAYS.between(periodStart, periodEnd));
        double weeksInPeriod = periodDays / 7.0;

        return ExerciseAnalytics.builder()
                .exerciseId(exerciseId)
                .exerciseName(exerciseName)
                .totalVolume(totalVolume)
                .sessionsCount(sessionsCount)
                .averageVolumePerSession(sessionsCount > 0 ?
                        totalVolume.divide(BigDecimal.valueOf(sessionsCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .maxWeight(maxWeight)
                .estimated1RM(estimated1RM)
                .totalSets(totalSets)
                .frequencyPerWeek(sessionsCount / Math.max(1, weeksInPeriod))
                .volumeTrend(calculateTrend(exercises, "VOLUME"))
                .strengthTrend(calculateTrend(exercises, "STRENGTH"))
                .progressScore(calculateProgressScore(exercises))
                .build();
    }

    // ==================== TREND LOGIC (Enhanced) ====================

    private String calculateTrend(List<WorkoutExercise> exercises, String type) {
        if (exercises.size() < 3) return "STABLE"; // Not enough data

        // Split into first half vs second half
        int midPoint = exercises.size() / 2;
        List<WorkoutExercise> firstHalf = exercises.subList(0, midPoint);
        List<WorkoutExercise> secondHalf = exercises.subList(midPoint, exercises.size());

        BigDecimal firstAvg;
        BigDecimal secondAvg;

        if ("VOLUME".equals(type)) {
            firstAvg = getAverageVolume(firstHalf);
            secondAvg = getAverageVolume(secondHalf);
        } else {
            // STRENGTH (Max Weight)
            firstAvg = getAverageMaxWeight(firstHalf);
            secondAvg = getAverageMaxWeight(secondHalf);
        }

        if (firstAvg.compareTo(BigDecimal.ZERO) == 0) return "STABLE";

        // Calculate % change
        BigDecimal change = secondAvg.subtract(firstAvg)
                .divide(firstAvg, 4, RoundingMode.HALF_UP);

        if (change.compareTo(BigDecimal.valueOf(0.05)) > 0) return "INCREASING"; // > 5% gain
        if (change.compareTo(BigDecimal.valueOf(-0.05)) < 0) return "DECREASING"; // > 5% loss
        return "STABLE";
    }

    private Double calculateProgressScore(List<WorkoutExercise> exercises) {
        if (exercises.size() < 2) return 50.0;

        // Compare first session vs last session estimated 1RM
        BigDecimal start1RM = calculateExercise1RM(exercises.get(0));
        BigDecimal end1RM = calculateExercise1RM(exercises.get(exercises.size() - 1));

        if (start1RM.compareTo(BigDecimal.ZERO) == 0) return 50.0;

        BigDecimal improvement = end1RM.subtract(start1RM)
                .divide(start1RM, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Base score 50 + improvement points
        double score = 50.0 + improvement.doubleValue();
        return Math.min(100.0, Math.max(0.0, score)); // Clamp between 0-100
    }

    // ==================== HELPER METHODS ====================

    private BigDecimal getAverageVolume(List<WorkoutExercise> exercises) {
        if (exercises.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = exercises.stream()
                .map(ex -> ex.getTotalVolume() != null ? ex.getTotalVolume() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(exercises.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal getAverageMaxWeight(List<WorkoutExercise> exercises) {
        if (exercises.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = exercises.stream()
                .map(ex -> ex.getMaxWeight() != null ? ex.getMaxWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(exercises.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateExercise1RM(WorkoutExercise exercise) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) return BigDecimal.ZERO;
        return exercise.getSets().stream()
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .map(this::calculate1RMFromSet)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal calculate1RMFromSet(WorkoutSet set) {
        // Epley Formula: weight * (1 + reps/30)
        BigDecimal weight = set.getWeightKg();
        double reps = set.getReps();
        if (reps == 1) return weight;
        return weight.multiply(BigDecimal.valueOf(1 + (reps / 30.0)));
    }

    private int countNewPRs(List<Workout> workouts) {
        return workouts.stream()
                .mapToInt(w -> w.getExercises().stream()
                        .mapToInt(e -> e.getSets().stream()
                                .mapToInt(s -> (s.getPersonalRecords() != null && !s.getPersonalRecords().isEmpty()) ? 1 : 0)
                                .sum())
                        .sum())
                .sum();
    }

    // ... (generateInsights and empty builder logic remain similar to previous, kept clean here)

    public Mono<List<String>> generateInsights(WorkoutAnalytics analytics) {
        List<String> insights = new ArrayList<>();
        if (analytics.getWorkoutFrequencyPerWeek() < 2) insights.add("Try to workout at least 2 times a week.");
        if (analytics.getStrengthProgressionScore() > 60) insights.add("Great strength progression detected!");
        return Mono.just(insights);
    }

    private WorkoutAnalytics buildEmptyAnalytics(WorkoutAnalytics.WorkoutAnalyticsBuilder builder) {
        return builder.totalWorkouts(0).totalDurationMinutes(0).averageDurationMinutes(0.0)
                .totalVolume(BigDecimal.ZERO).workoutFrequencyPerWeek(0.0).newPRsCount(0)
                .exerciseAnalytics(Collections.emptyList()).build();
    }

    private String calculateOverallVolumeTrend(List<Workout> workouts) {
        if (workouts.size() < 2) return "STABLE";
        // Simplified Logic: Compare first 3 vs last 3
        return "STABLE"; // Placeholder implementation for overall
    }

    private Double calculateVolumeChangePercentage(List<Workout> workouts) {
        return 0.0; // Placeholder
    }

    private Double calculateAverageProgressScore(List<ExerciseAnalytics> ea) {
        return ea.stream().mapToDouble(ExerciseAnalytics::getProgressScore).average().orElse(0.0);
    }
}