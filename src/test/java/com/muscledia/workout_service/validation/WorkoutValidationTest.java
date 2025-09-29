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

}