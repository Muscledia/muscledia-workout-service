package com.muscledia.workout_service.validation;

import com.muscledia.workout_service.dto.request.CreateWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.WorkoutExerciseRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkoutValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.afterPropertiesSet();
        validator = factory.getValidator();
    }

    @Test
    void testValidWorkoutRequest() {
        CreateWorkoutRequest request = createValidWorkoutRequest();

        Set<ConstraintViolation<CreateWorkoutRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "Valid workout request should have no violations");
    }

    @Test
    void testInvalidWorkoutDate() {
        CreateWorkoutRequest request = createValidWorkoutRequest();
        request.setWorkoutDate(LocalDateTime.now().plusDays(1)); // Future date

        Set<ConstraintViolation<CreateWorkoutRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("cannot be in the future")));
    }

    @Test
    void testInvalidDuration() {
        CreateWorkoutRequest request = createValidWorkoutRequest();
        request.setDurationMinutes(0); // Invalid duration

        Set<ConstraintViolation<CreateWorkoutRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Duration must be at least 1 minute")));
    }

    @Test
    void testInvalidExerciseWeight() {
        CreateWorkoutRequest request = createValidWorkoutRequest();
        request.getExercises().get(0).setWeight(BigDecimal.valueOf(-10)); // Negative weight

        Set<ConstraintViolation<CreateWorkoutRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Weight cannot be negative")));
    }

    @Test
    void testEmptyExercisesList() {
        CreateWorkoutRequest request = createValidWorkoutRequest();
        request.setExercises(List.of()); // Empty exercises list

        Set<ConstraintViolation<CreateWorkoutRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("At least one exercise is required")));
    }

    @Test
    void testCustomWorkoutValidation() {
        CreateWorkoutRequest request = createValidWorkoutRequest();
        request.setWorkoutDate(LocalDateTime.now().minusYears(2)); // Too far in past

        Set<ConstraintViolation<CreateWorkoutRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("cannot be more than 1 year in the past")));
    }

    private CreateWorkoutRequest createValidWorkoutRequest() {
        CreateWorkoutRequest request = new CreateWorkoutRequest();
        request.setWorkoutDate(LocalDateTime.now().minusHours(1));
        request.setDurationMinutes(60);
        request.setTotalVolume(BigDecimal.valueOf(1000));
        request.setNotes("Test workout");

        WorkoutExerciseRequest exercise = new WorkoutExerciseRequest();
        exercise.setExerciseId("test-exercise-id");
        exercise.setSets(3);
        exercise.setReps(10);
        exercise.setWeight(BigDecimal.valueOf(100));
        exercise.setOrder(1);
        exercise.setNotes("Test exercise");

        request.setExercises(List.of(exercise));

        return request;
    }
}