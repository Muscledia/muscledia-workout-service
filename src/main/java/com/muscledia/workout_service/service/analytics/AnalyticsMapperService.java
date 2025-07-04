package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.dto.response.analytics.ExerciseAnalyticsResponse;
import com.muscledia.workout_service.dto.response.analytics.PersonalRecordResponse;
import com.muscledia.workout_service.dto.response.analytics.WorkoutAnalyticsResponse;
import com.muscledia.workout_service.model.analytics.ExerciseAnalytics;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.analytics.WorkoutAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsMapperService {

    /**
     * Convert WorkoutAnalytics to WorkoutAnalyticsResponse
     */
    public WorkoutAnalyticsResponse toResponse(WorkoutAnalytics analytics) {
        WorkoutAnalyticsResponse response = new WorkoutAnalyticsResponse();

        // Basic analytics data
        response.setAnalysisPeriod(analytics.getAnalysisPeriod());
        response.setPeriodStart(analytics.getPeriodStart());
        response.setPeriodEnd(analytics.getPeriodEnd());

        // Workout statistics
        response.setTotalWorkouts(analytics.getTotalWorkouts());
        response.setTotalDurationMinutes(analytics.getTotalDurationMinutes());
        response.setAverageDurationMinutes(analytics.getAverageDurationMinutes());
        response.setTotalVolume(analytics.getTotalVolume());
        response.setAverageVolumePerWorkout(analytics.getAverageVolumePerWorkout());
        response.setWorkoutFrequencyPerWeek(analytics.getWorkoutFrequencyPerWeek());

        // Progress metrics
        response.setVolumeTrend(analytics.getVolumeTrend());
        response.setVolumeChangePercentage(analytics.getVolumeChangePercentage());
        response.setStrengthProgressionScore(analytics.getStrengthProgressionScore());

        // Personal records
        response.setNewPRsCount(analytics.getNewPRsCount());
        if (analytics.getRecentPRs() != null) {
            response.setRecentPRs(analytics.getRecentPRs().stream()
                    .map(this::toPersonalRecordResponse)
                    .collect(Collectors.toList()));
        }

        // Exercise analytics
        if (analytics.getExerciseAnalytics() != null) {
            response.setExerciseAnalytics(analytics.getExerciseAnalytics().stream()
                    .map(this::toExerciseAnalyticsResponse)
                    .collect(Collectors.toList()));
        }

        // Generate insights and recommendations
        response.setInsights(generateInsights(analytics));
        response.setRecommendations(generateRecommendations(analytics));

        return response;
    }

    /**
     * Convert ExerciseAnalytics to ExerciseAnalyticsResponse
     */
    public ExerciseAnalyticsResponse toExerciseAnalyticsResponse(ExerciseAnalytics analytics) {
        ExerciseAnalyticsResponse response = new ExerciseAnalyticsResponse();

        response.setExerciseId(analytics.getExerciseId());
        response.setExerciseName(analytics.getExerciseName());

        // Volume metrics
        response.setTotalVolume(analytics.getTotalVolume());
        response.setAverageVolumePerSession(analytics.getAverageVolumePerSession());
        response.setVolumeTrend(analytics.getVolumeTrend());
        response.setVolumeChangePercentage(analytics.getVolumeChangePercentage());

        // Strength metrics
        response.setMaxWeight(analytics.getMaxWeight());
        response.setAverageWeight(analytics.getAverageWeight());
        response.setEstimated1RM(analytics.getEstimated1RM());
        response.setStrengthTrend(analytics.getStrengthTrend());

        // Performance metrics
        response.setTotalSets(analytics.getTotalSets());
        response.setTotalReps(analytics.getTotalReps());
        response.setAverageSetsPerSession(analytics.getAverageSetsPerSession());
        response.setAverageRepsPerSet(analytics.getAverageRepsPerSet());
        response.setFrequencyPerWeek(analytics.getFrequencyPerWeek());

        // Progress tracking
        response.setFirstRecorded(analytics.getFirstRecorded());
        response.setLastRecorded(analytics.getLastRecorded());
        response.setSessionsCount(analytics.getSessionsCount());
        response.setProgressScore(analytics.getProgressScore());

        // Current PR
        if (analytics.getCurrentPR() != null) {
            response.setCurrentPR(toPersonalRecordResponse(analytics.getCurrentPR()));
        }

        return response;
    }

    /**
     * Convert PersonalRecord to PersonalRecordResponse
     */
    public PersonalRecordResponse toPersonalRecordResponse(PersonalRecord pr) {
        PersonalRecordResponse response = new PersonalRecordResponse();

        response.setId(pr.getId());
        response.setExerciseId(pr.getExerciseId());
        response.setExerciseName(pr.getExerciseName());
        response.setRecordType(pr.getRecordType());
        response.setValue(pr.getValue());
        response.setWeight(pr.getWeight());
        response.setReps(pr.getReps());
        response.setSets(pr.getSets());
        response.setAchievedDate(pr.getAchievedDate());
        response.setPreviousRecord(pr.getPreviousRecord());
        response.setImprovementPercentage(pr.getImprovementPercentage());
        response.setNotes(pr.getNotes());

        // Calculate days since last PR
        if (pr.getAchievedDate() != null) {
            long daysSince = ChronoUnit.DAYS.between(pr.getAchievedDate(), LocalDateTime.now());
            response.setDaysSinceLastPR(daysSince);
            response.setIsRecentAchievement(daysSince <= 30);
        }

        return response;
    }

    /**
     * Generate insights from analytics data
     */
    private List<String> generateInsights(WorkoutAnalytics analytics) {
        List<String> insights = new ArrayList<>();

        // Workout frequency insights
        if (analytics.getWorkoutFrequencyPerWeek() != null) {
            if (analytics.getWorkoutFrequencyPerWeek() < 2) {
                insights.add("💡 Consider increasing workout frequency to 3-4 times per week for optimal progress");
            } else if (analytics.getWorkoutFrequencyPerWeek() > 6) {
                insights.add("⚠️ High workout frequency detected - ensure you're getting adequate recovery");
            } else if (analytics.getWorkoutFrequencyPerWeek() >= 3 && analytics.getWorkoutFrequencyPerWeek() <= 5) {
                insights.add("✅ Great workout frequency! You're maintaining a consistent training schedule");
            }
        }

        // Volume progression insights
        if (analytics.getVolumeChangePercentage() != null) {
            if (analytics.getVolumeChangePercentage() > 15) {
                insights.add("🚀 Excellent volume progression! Your training intensity is increasing steadily");
            } else if (analytics.getVolumeChangePercentage() > 5) {
                insights.add("📈 Good volume progression - keep up the consistent improvement");
            } else if (analytics.getVolumeChangePercentage() < -10) {
                insights.add("📉 Volume has decreased significantly - consider reviewing your training program");
            }
        }

        // Strength progression insights
        if (analytics.getStrengthProgressionScore() != null) {
            if (analytics.getStrengthProgressionScore() > 80) {
                insights.add("💪 Outstanding strength gains! Your training is highly effective");
            } else if (analytics.getStrengthProgressionScore() > 60) {
                insights.add("👍 Good strength progression - you're moving in the right direction");
            } else if (analytics.getStrengthProgressionScore() < 40) {
                insights.add("🎯 Strength progression could be improved - consider progressive overload techniques");
            }
        }

        // Duration insights
        if (analytics.getAverageDurationMinutes() != null) {
            if (analytics.getAverageDurationMinutes() > 120) {
                insights.add("⏱️ Long workout sessions - consider optimizing for efficiency and recovery");
            } else if (analytics.getAverageDurationMinutes() < 30) {
                insights.add("⚡ Short, efficient workouts - great for maintaining consistency!");
            }
        }

        // Exercise variety insights
        if (analytics.getExerciseAnalytics() != null) {
            long stagnantExercises = analytics.getExerciseAnalytics().stream()
                    .filter(ex -> "STABLE".equals(ex.getStrengthTrend()) && ex.getSessionsCount() > 10)
                    .count();

            if (stagnantExercises > 0) {
                insights.add(String.format(
                        "🔄 %d exercises have plateaued - consider exercise variations or rep/set schemes",
                        stagnantExercises));
            }
        }

        // PR insights
        if (analytics.getNewPRsCount() != null && analytics.getNewPRsCount() > 0) {
            insights.add(String.format("🏆 Congratulations on %d new personal records this period!",
                    analytics.getNewPRsCount()));
        }

        return insights;
    }

    /**
     * Generate recommendations based on analytics
     */
    private List<String> generateRecommendations(WorkoutAnalytics analytics) {
        List<String> recommendations = new ArrayList<>();

        // Frequency recommendations
        if (analytics.getWorkoutFrequencyPerWeek() != null && analytics.getWorkoutFrequencyPerWeek() < 3) {
            recommendations.add("Add 1-2 more workout sessions per week to accelerate progress");
        }

        // Volume recommendations
        if (analytics.getVolumeChangePercentage() != null && analytics.getVolumeChangePercentage() < 5) {
            recommendations.add("Gradually increase training volume by adding sets or weight to continue progressing");
        }

        // Exercise-specific recommendations
        if (analytics.getExerciseAnalytics() != null) {
            // Find exercises with low frequency
            analytics.getExerciseAnalytics().stream()
                    .filter(ex -> ex.getFrequencyPerWeek() < 1.5 && ex.getProgressScore() < 60)
                    .findFirst()
                    .ifPresent(ex -> recommendations.add("Increase frequency for " + ex.getExerciseName()
                            + " to twice per week for better progress"));

            // Find exercises with high volume but low progression
            analytics.getExerciseAnalytics().stream()
                    .filter(ex -> ex.getTotalSets() > 20 && ex.getProgressScore() < 50)
                    .findFirst()
                    .ifPresent(ex -> recommendations
                            .add("Consider deload week or technique focus for " + ex.getExerciseName()));
        }

        // Duration recommendations
        if (analytics.getAverageDurationMinutes() != null && analytics.getAverageDurationMinutes() > 90) {
            recommendations.add("Consider splitting longer sessions or reducing rest periods for better recovery");
        }

        // Strength progression recommendations
        if (analytics.getStrengthProgressionScore() != null && analytics.getStrengthProgressionScore() < 60) {
            recommendations.add("Focus on progressive overload - gradually increase weight, reps, or sets each week");
            recommendations.add("Ensure adequate protein intake (0.8-1g per lb bodyweight) for muscle growth");
            recommendations.add("Prioritize sleep (7-9 hours) and recovery for optimal strength gains");
        }

        return recommendations;
    }
}