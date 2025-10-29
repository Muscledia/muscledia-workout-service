package com.muscledia.workout_service.service.analytics;

import com.muscledia.workout_service.dto.response.analytics.PRStatistics;
import com.muscledia.workout_service.event.PersonalRecordEvent;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface Segregation Principle: Focused interface for PR processing
 * Dependency Inversion Principle: Allows different implementations
 *
 * This service is responsible for:
 * 1. Analyzing workout data for personal records
 * 2. Creating PersonalRecordEvent objects for new PRs
 * 3. NOT publishing events (that's the publisher's job - Single Responsibility)
 */
public interface IPersonalRecordService {

    /**
     * Process a completed workout and return any personal records found
     *
     * @param workoutId ID of the completed workout
     * @param userId ID of the user who completed the workout
     * @return Mono containing list of PersonalRecordEvent objects (empty if no PRs found)
     */
    Mono<List<PersonalRecordEvent>> processWorkoutForPersonalRecords(String workoutId, Long userId);

    /**
     * Alternative method if you have the full workout object
     *
     * @param workout The completed workout to analyze
     * @return Mono containing list of PersonalRecordEvent objects
     */
    Mono<List<PersonalRecordEvent>> processWorkoutForPersonalRecords(Workout workout);

    // Keep all your existing query methods
    Flux<PersonalRecord> getExercisePRs(Long userId, String exerciseId);
    Flux<PersonalRecord> getAllPersonalRecords(Long userId);
    Flux<PersonalRecord> getPersonalRecords(Long userId, String exerciseId);
    Mono<PersonalRecord> getPersonalRecord(Long userId, String exerciseId, String recordType);
    Mono<List<PersonalRecord>> getRecentPRs(Long userId, int days);
    Mono<PRStatistics> getPRStatistics(Long userId);
    Flux<PersonalRecord> getAllPRs(Long userId);
    Mono<Boolean> wouldBePR(Long userId, String exerciseId, String recordType, BigDecimal value);
    Mono<Void> deletePR(String prId, Long userId);

    // Keep your existing detection method
    Mono<List<String>> detectQualifyingPRsForSet(Long userId, String exerciseId, String exerciseName, WorkoutSet set);
}