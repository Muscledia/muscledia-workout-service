package com.muscledia.workout_service.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import com.muscledia.workout_service.model.enums.WorkoutStatus;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.muscledia.workout_service.model.Workout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface WorkoutRepository extends ReactiveMongoRepository<Workout, String> {

    /**
     * Find workout by ID and user ID (ensures user can only access their own workouts)
     */
    Mono<Workout> findByIdAndUserId(String id, Long userId);

    /**
     * Find all workouts for a user, ordered by workout date descending (most recent first)
     */
    Flux<Workout> findByUserIdOrderByWorkoutDateDesc(Long userId);

    /**
     * FIXED: Find workouts by user ID and date range
     */
    Flux<Workout> findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    // VOLUME-BASED QUERIES

    /**
     * Find workouts by total volume range
     */
    Flux<Workout> findByUserIdAndTotalVolumeBetweenOrderByWorkoutDateDesc(
            Long userId, BigDecimal minVolume, BigDecimal maxVolume);

    /**
     * Find workouts with minimum total volume
     */
    Flux<Workout> findByUserIdAndTotalVolumeGreaterThanEqual(Long userId, BigDecimal minVolume);

    // DURATION-BASED QUERIES

    /**
     * Find workouts by duration range (in minutes)
     */
    Flux<Workout> findByUserIdAndDurationMinutesBetweenOrderByWorkoutDateDesc(
            Long userId, Integer minDuration, Integer maxDuration);

    // STATUS-BASED QUERIES

    /**
     * Find active workouts for a user (workouts that are in progress)
     */
    @Query("{ 'userId': ?0, 'status': 'IN_PROGRESS' }")
    Flux<Workout> findActiveWorkoutsByUserId(Long userId);

    /**
     * Find completed workouts for a user
     */
    @Query("{ 'userId': ?0, 'status': 'COMPLETED' }")
    Flux<Workout> findCompletedWorkoutsByUserId(Long userId);

    /**
     * Find workouts by status
     */
    Flux<Workout> findByUserIdAndStatusOrderByWorkoutDateDesc(Long userId, WorkoutStatus status);

    // LIMITED RESULT QUERIES

    /**
     * Find recent workouts (limit results to top 10)
     */
    Flux<Workout> findTop10ByUserIdOrderByWorkoutDateDesc(Long userId);

    /**
     * Find recent completed workouts
     */
    @Query(value = "{ 'userId': ?0, 'status': 'COMPLETED' }", sort = "{ 'workoutDate': -1 }")
    Flux<Workout> findTop5CompletedWorkoutsByUserIdOrderByWorkoutDateDesc(Long userId);

    // COUNT QUERIES

    /**
     * Count workouts in date range
     */
    Mono<Long> countByUserIdAndWorkoutDateBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Count total workouts for a user
     */
    Mono<Long> countByUserId(Long userId);

    /**
     * Count completed workouts for a user
     */
    @Query(value = "{ 'userId': ?0, 'status': 'COMPLETED' }", count = true)
    Mono<Long> countCompletedWorkoutsByUserId(Long userId);

    // EXERCISE-BASED QUERIES

    /**
     * FIXED: Find workouts that contain a specific exercise
     */
    @Query("{ 'userId': ?0, 'exercises.exerciseId': ?1 }")
    Flux<Workout> findByUserIdAndExerciseId(Long userId, String exerciseId);

    /**
     * Find workouts containing exercises from a list
     */
    @Query("{ 'userId': ?0, 'exercises.exerciseId': { $in: ?1 } }")
    Flux<Workout> findByUserIdAndExerciseIdIn(Long userId, java.util.List<String> exerciseIds);

    // WORKOUT PLAN BASED QUERIES

    /**
     * Find workouts by user ID and workout plan ID
     */
    Flux<Workout> findByUserIdAndWorkoutPlanIdOrderByWorkoutDateDesc(Long userId, String workoutPlanId);

    // SEARCH AND FILTER QUERIES

    /**
     * Find workouts with notes containing specific text
     */
    Flux<Workout> findByUserIdAndNotesContainingIgnoreCase(Long userId, String searchText);

    /**
     * Find workouts by workout type
     */
    Flux<Workout> findByUserIdAndWorkoutTypeIgnoreCaseOrderByWorkoutDateDesc(Long userId, String workoutType);

    /**
     * Find workouts by location
     */
    Flux<Workout> findByUserIdAndLocationIgnoreCaseOrderByWorkoutDateDesc(Long userId, String location);

    // DATE-BASED SPECIALIZED QUERIES

    /**
     * Find workouts for a specific month and year
     */
    @Query("{ 'userId': ?0, 'workoutDate': { '$gte': ?1, '$lt': ?2 } }")
    Flux<Workout> findWorkoutsByMonth(Long userId, LocalDateTime monthStart, LocalDateTime monthEnd);

    /**
     * Find workouts for today
     */
    @Query("{ 'userId': ?0, 'workoutDate': { '$gte': ?1, '$lt': ?2 } }")
    Flux<Workout> findWorkoutsForToday(Long userId, LocalDateTime dayStart, LocalDateTime dayEnd);

    /**
     * Find workouts in the last N days
     */
    @Query("{ 'userId': ?0, 'workoutDate': { '$gte': ?1 } }")
    Flux<Workout> findWorkoutsInLastDays(Long userId, LocalDateTime cutoffDate);

    // PERFORMANCE AND ANALYTICS QUERIES

    /**
     * Find workouts with rating above threshold
     */
    @Query("{ 'userId': ?0, 'rating': { '$gte': ?1 } }")
    Flux<Workout> findWorkoutsByMinRating(Long userId, Integer minRating);

    /**
     * Find best workouts (highest volume)
     */
    @Query(value = "{ 'userId': ?0, 'status': 'COMPLETED' }", sort = "{ 'totalVolume': -1 }")
    Flux<Workout> findBestWorkoutsByVolume(Long userId);

    /**
     * Find longest workouts
     */
    @Query(value = "{ 'userId': ?0, 'status': 'COMPLETED' }", sort = "{ 'durationMinutes': -1 }")
    Flux<Workout> findLongestWorkouts(Long userId);

    // ADMIN QUERIES (for system-wide analytics)

    /**
     * Find all workouts (admin use only)
     */
    @Query(value = "{}", sort = "{ 'workoutDate': -1 }")
    Flux<Workout> findAllOrderByWorkoutDateDesc();

    /**
     * Count all workouts in the system
     */
    @Query(value = "{}", count = true)
    Mono<Long> countAllWorkouts();

    /**
     * Find workouts by multiple user IDs (for batch operations)
     */
    @Query("{ 'userId': { $in: ?0 } }")
    Flux<Workout> findByUserIdIn(java.util.List<Long> userIds);

    // AGGREGATION-STYLE QUERIES

    /**
     * Find workouts with specific tags
     */
    @Query("{ 'userId': ?0, 'tags': { $in: ?1 } }")
    Flux<Workout> findByUserIdAndTagsIn(Long userId, java.util.List<String> tags);

    /**
     * Find workouts without any tags
     */
    @Query("{ 'userId': ?0, '$or': [ { 'tags': { $exists: false } }, { 'tags': { $size: 0 } } ] }")
    Flux<Workout> findWorkoutsWithoutTags(Long userId);

    // COMPLEX COMPOUND QUERIES

    /**
     * Find workouts by multiple criteria
     */
    @Query("{ '$and': [ " +
            "  { 'userId': ?0 }, " +
            "  { 'workoutDate': { '$gte': ?1, '$lte': ?2 } }, " +
            "  { 'status': ?3 }, " +
            "  { 'totalVolume': { '$gte': ?4 } } " +
            "] }")
    Flux<Workout> findByMultipleCriteria(Long userId, LocalDateTime startDate, LocalDateTime endDate,
                                         WorkoutStatus status, BigDecimal minVolume);

    /**
     * Advanced search with optional parameters
     */
    @Query("{ '$and': [ " +
            "  { 'userId': ?0 }, " +
            "  ?#{ [1] != null ? { 'workoutType': { $regex: [1], $options: 'i' } } : {} }, " +
            "  ?#{ [2] != null ? { 'location': { $regex: [2], $options: 'i' } } : {} }, " +
            "  ?#{ [3] != null ? { 'rating': { $gte: [3] } } : {} } " +
            "] }")
    Flux<Workout> findWithOptionalFilters(Long userId, String workoutType, String location, Integer minRating);

    Flux<Workout> findByUserIdAndWorkoutDateGreaterThanEqualOrderByWorkoutDateDesc(Long userId, LocalDateTime startDate);

    Flux<Workout> findByUserIdAndWorkoutDateLessThanEqualOrderByWorkoutDateDesc(Long userId, LocalDateTime endDate);

    /**
     * Find workouts for a user within a date range
     * Using LocalDateTime to match the Workout.completedAt field type
     */
    Flux<Workout> findByUserIdAndCompletedAtBetween(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
