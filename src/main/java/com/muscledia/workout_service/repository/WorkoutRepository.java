package com.muscledia.workout_service.repository;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.muscledia.workout_service.model.Workout;

@Repository
public interface WorkoutRepository extends MongoRepository<Workout, String> {
    // Find workouts by user ID, ordered by date
    List<Workout> findByUserIdOrderByWorkoutDateDesc(Long userId);

    // Find workouts within a date range for a user
    List<Workout> findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate);

    // Find workouts by total volume range
    List<Workout> findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(
            Long userId,
            BigDecimal minVolume,
            BigDecimal maxVolume);

    // Find workouts by duration range (in minutes)
    List<Workout> findByUserIdAndDurationMinutesBetweenOrderByWorkoutDateDesc(
            Long userId,
            Integer minDuration,
            Integer maxDuration);

    // Find recent workouts (limit results)
    List<Workout> findTop10ByUserIdOrderByWorkoutDateDesc(Long userId);

    // Count workouts in date range
    long countByUserIdAndWorkoutDateBetween(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate);

    // Custom query to find workouts that contain a specific exercise
    @Query("{'userId': ?0, 'exercises.exerciseId': ?1}")
    List<Workout> findByUserIdAndExerciseId(Long userId, String exerciseId);

    // Find workouts with minimum total volume
    List<Workout> findByUserIdAndTotalVolumeGreaterThanEqual(
            Long userId,
            BigDecimal minVolume);

    // Find workouts for a specific month and year
    @Query("{'userId': ?0, 'workoutDate': {'$gte': ?1, '$lt': ?2}}")
    List<Workout> findWorkoutsByMonth(
            Long userId,
            LocalDateTime monthStart,
            LocalDateTime monthEnd);

    // Find workouts with notes containing specific text
    List<Workout> findByUserIdAndNotesContainingIgnoreCase(
            Long userId,
            String searchText);
}
