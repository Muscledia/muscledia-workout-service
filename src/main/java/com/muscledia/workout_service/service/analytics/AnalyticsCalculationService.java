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

        // Use builder pattern instead of new constructor
        WorkoutAnalytics.WorkoutAnalyticsBuilder analyticsBuilder = WorkoutAnalytics.builder()
                .userId(userId)
                .analysisPeriod(period)
                .periodStart(periodStart)
                .periodEnd(periodEnd);

        // Basic workout statistics
        if (workouts.isEmpty()) {
            return Mono.just(buildEmptyAnalytics(analyticsBuilder));
        }

        // Calculate basic stats
        int totalDuration = workouts.stream()
                .mapToInt(Workout::getDurationMinutes)
                .sum();

        BigDecimal totalVolume = workouts.stream()
                .map(Workout::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        double weeksInPeriod = periodDays / 7.0;

        analyticsBuilder
                .totalWorkouts(workouts.size())
                .totalDurationMinutes(totalDuration)
                .averageDurationMinutes((double) totalDuration / workouts.size())
                .totalVolume(totalVolume)
                .averageVolumePerWorkout(
                        totalVolume.divide(BigDecimal.valueOf(workouts.size()), 2, RoundingMode.HALF_UP))
                .workoutFrequencyPerWeek(workouts.size() / weeksInPeriod);

        // Exercise-specific analytics
        List<ExerciseAnalytics> exerciseAnalytics = calculateExerciseAnalytics(workouts, periodStart, periodEnd);
        analyticsBuilder.exerciseAnalytics(exerciseAnalytics);

        // Progress metrics
        analyticsBuilder
                .volumeTrend(calculateOverallVolumeTrend(workouts))
                .volumeChangePercentage(calculateVolumeChangePercentage(workouts))
                .strengthProgressionScore(calculateAverageProgressScore(exerciseAnalytics))
                .newPRsCount(0); // Placeholder

        return Mono.just(analyticsBuilder.build());
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

        // Volume calculations
        BigDecimal totalVolume = exercises.stream()
                .map(WorkoutExercise::getTotalVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int sessionsCount = exercises.size();

        // Weight statistics
        BigDecimal maxWeight = exercises.stream()
                .map(WorkoutExercise::getMaxWeight)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal avgWeight = exercises.stream()
                .map(WorkoutExercise::getMaxWeight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(exercises.size()), 2, RoundingMode.HALF_UP);

        // 1RM estimation
        BigDecimal estimated1RM = exercises.stream()
                .filter(exercise -> exercise.getSets() != null && !exercise.getSets().isEmpty())
                .map(this::calculateExercise1RM)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // Set and rep statistics
        int totalSets = exercises.stream()
                .mapToInt(exercise -> exercise.getSets() != null ? exercise.getSets().size() : 0)
                .sum();

        int totalReps = exercises.stream()
                .mapToInt(WorkoutExercise::getTotalReps)
                .sum();

        // Frequency calculation
        long periodDays = ChronoUnit.DAYS.between(periodStart, periodEnd);
        double weeksInPeriod = periodDays / 7.0;

        return ExerciseAnalytics.builder()
                .exerciseId(exerciseId)
                .exerciseName(exerciseName)
                .totalVolume(totalVolume)
                .sessionsCount(sessionsCount)
                .averageVolumePerSession(
                        sessionsCount > 0 ? totalVolume.divide(BigDecimal.valueOf(sessionsCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .maxWeight(maxWeight)
                .averageWeight(avgWeight)
                .estimated1RM(estimated1RM)
                .totalSets(totalSets)
                .totalReps(totalReps)
                .averageSetsPerSession((double) totalSets / sessionsCount)
                .averageRepsPerSet(totalSets > 0 ? (double) totalReps / totalSets : 0.0)
                .frequencyPerWeek(sessionsCount / weeksInPeriod)
                .volumeTrend(calculateTrend(exercises, "VOLUME"))
                .strengthTrend(calculateTrend(exercises, "STRENGTH"))
                .progressScore(calculateProgressScore(exercises))
                .build();
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

    private WorkoutAnalytics buildEmptyAnalytics(WorkoutAnalytics.WorkoutAnalyticsBuilder builder) {
        return builder
                .totalWorkouts(0)
                .totalDurationMinutes(0)
                .averageDurationMinutes(0.0)
                .totalVolume(BigDecimal.ZERO)
                .averageVolumePerWorkout(BigDecimal.ZERO)
                .workoutFrequencyPerWeek(0.0)
                .exerciseAnalytics(new ArrayList<>())
                .newPRsCount(0)
                .build();
    }

    /**
     * Calculate 1RM for an exercise using its heaviest set
     */
    private BigDecimal calculateExercise1RM(WorkoutExercise exercise) {
        if (exercise.getSets() == null || exercise.getSets().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return exercise.getSets().stream()
                .filter(set -> set.getWeightKg() != null && set.getReps() != null && set.getReps() > 0)
                .map(this::calculate1RMFromSet)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculate 1RM from a single set using Epley formula
     */
    private BigDecimal calculate1RMFromSet(com.muscledia.workout_service.model.embedded.WorkoutSet set) {
        BigDecimal weight = set.getWeightKg();
        Integer reps = set.getReps();

        if (weight == null || reps == null || reps <= 0) {
            return BigDecimal.ZERO;
        }

        if (reps == 1) {
            return weight;
        }

        // Epley formula: weight * (1 + reps/30)
        BigDecimal multiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
        return weight.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private String calculateTrend(List<WorkoutExercise> exercises, String type) {
        if (exercises.size() < 3) return "INSUFFICIENT_DATA";
        return "STABLE"; // Placeholder
    }

    private String calculateOverallVolumeTrend(List<Workout> workouts) {
        if (workouts.size() < 2) return "INSUFFICIENT_DATA";

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
        if (workouts.size() < 2) return 0.0;

        BigDecimal firstVolume = workouts.get(0).getTotalVolume();
        BigDecimal lastVolume = workouts.get(workouts.size() - 1).getTotalVolume();

        if (firstVolume == null || lastVolume == null || firstVolume.equals(BigDecimal.ZERO)) {
            return 0.0;
        }

        return lastVolume.subtract(firstVolume)
                .divide(firstVolume, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private Double calculateAverageProgressScore(List<ExerciseAnalytics> exerciseAnalytics) {
        return exerciseAnalytics.stream()
                .mapToDouble(ExerciseAnalytics::getProgressScore)
                .filter(score -> score > 0)
                .average()
                .orElse(0.0);
    }

    private Double calculateProgressScore(List<WorkoutExercise> exercises) {
        if (exercises.size() < 3) return 50.0;
        return 75.0; // Placeholder
    }

    private String getExerciseName(String exerciseId) {
        return "Exercise " + exerciseId.substring(0, Math.min(8, exerciseId.length()));
    }
}