package com.muscledia.workout_service.validation;


import com.muscledia.workout_service.domain.service.WorkoutValidationService;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkoutValidationService - Set Operations Validation")
public class WorkoutValidationServiceSetOperationsTest {

    private WorkoutValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new WorkoutValidationService();
    }

    @Test
    @DisplayName("Should pass validation when workout is IN_PROGRESS")
    void shouldPassValidationWhenWorkoutInProgress() {
        Workout workout = createWorkout(WorkoutStatus.IN_PROGRESS);

        var result = validationService.validateForSetOperations(workout);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when workout is PLANNED")
    void shouldFailValidationWhenWorkoutIsPlanned() {
        Workout workout = createWorkout(WorkoutStatus.PLANNED);

        var result = validationService.validateForSetOperations(workout);

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getErrorMessage()).contains("IN_PROGRESS");
        assertThat(result.getErrorMessage()).contains("PLANNED");
    }

    @Test
    @DisplayName("Should fail validation when workout is COMPLETED")
    void shouldFailValidationWhenWorkoutIsCompleted() {
        Workout workout = createWorkout(WorkoutStatus.COMPLETED);

        var result = validationService.validateForSetOperations(workout);

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getErrorMessage()).contains("IN_PROGRESS");
        assertThat(result.getErrorMessage()).contains("COMPLETED");
    }

    @Test
    @DisplayName("Should fail validation when workout is CANCELLED")
    void shouldFailValidationWhenWorkoutIsCancelled() {
        Workout workout = createWorkout(WorkoutStatus.CANCELLED);

        var result = validationService.validateForSetOperations(workout);

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getErrorMessage()).contains("IN_PROGRESS");
        assertThat(result.getErrorMessage()).contains("CANCELLED");
    }

    @Test
    @DisplayName("Should fail validation when workout is null")
    void shouldFailValidationWhenWorkoutIsNull() {
        var result = validationService.validateForSetOperations(null);

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getErrorMessage()).contains("cannot be null");
    }

    private Workout createWorkout(WorkoutStatus status) {
        Workout workout = new Workout();
        workout.setId("test-workout-123");
        workout.setUserId(1L);
        workout.setWorkoutName("Test Workout");
        workout.setStatus(status);
        workout.setWorkoutType("STRENGTH");
        workout.setWorkoutDate(LocalDateTime.now());
        return workout;
    }
}
