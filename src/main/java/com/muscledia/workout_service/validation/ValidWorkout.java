package com.muscledia.workout_service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WorkoutValidator.class)
@Documented
public @interface ValidWorkout {
    String message() default "Invalid workout data";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}