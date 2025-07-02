package com.muscledia.workout_service.validation;

import com.muscledia.workout_service.dto.request.CreateWorkoutRequest;
import com.muscledia.workout_service.dto.request.UpdateWorkoutRequest;
import com.muscledia.workout_service.dto.request.embedded.WorkoutExerciseRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class WorkoutValidator implements ConstraintValidator<ValidWorkout, Object> {

    @Override
    public void initialize(ValidWorkout constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }

        context.disableDefaultConstraintViolation();
        boolean isValid = true;

        if (value instanceof CreateWorkoutRequest request) {
            isValid = validateWorkoutRequest(request.getWorkoutDate(), request.getDurationMinutes(),
                    request.getTotalVolume(), request.getExercises(), context);
        } else if (value instanceof UpdateWorkoutRequest request) {
            isValid = validateWorkoutRequest(request.getWorkoutDate(), request.getDurationMinutes(),
                    request.getTotalVolume(), request.getExercises(), context);
        }

        return isValid;
    }

    private boolean validateWorkoutRequest(LocalDateTime workoutDate, Integer durationMinutes,
            BigDecimal totalVolume, List<WorkoutExerciseRequest> exercises,
            ConstraintValidatorContext context) {
        boolean isValid = true;

        // Validate workout date is not too far in the past (more than 1 year)
        if (workoutDate != null && workoutDate.isBefore(LocalDateTime.now().minusYears(1))) {
            context.buildConstraintViolationWithTemplate("Workout date cannot be more than 1 year in the past")
                    .addPropertyNode("workoutDate")
                    .addConstraintViolation();
            isValid = false;
        }

        // Validate exercise order uniqueness and sequence
        if (exercises != null && !exercises.isEmpty()) {
            Set<Integer> orders = new HashSet<>();
            Set<String> exerciseIds = new HashSet<>();

            for (int i = 0; i < exercises.size(); i++) {
                WorkoutExerciseRequest exercise = exercises.get(i);

                // Check for duplicate exercise orders
                if (exercise.getOrder() != null) {
                    if (!orders.add(exercise.getOrder())) {
                        context.buildConstraintViolationWithTemplate("Duplicate exercise order: " + exercise.getOrder())
                                .addPropertyNode("exercises[" + i + "].order")
                                .addConstraintViolation();
                        isValid = false;
                    }
                }

                // Check for duplicate exercises (same exercise performed multiple times)
                if (exercise.getExerciseId() != null) {
                    if (!exerciseIds.add(exercise.getExerciseId())) {
                        context.buildConstraintViolationWithTemplate("Exercise appears multiple times in workout")
                                .addPropertyNode("exercises[" + i + "].exerciseId")
                                .addConstraintViolation();
                        isValid = false;
                    }
                }

                // Validate reasonable weight-to-reps ratio
                if (exercise.getWeight() != null && exercise.getReps() != null) {
                    BigDecimal weightPerRep = exercise.getWeight().divide(BigDecimal.valueOf(exercise.getReps()), 2,
                            BigDecimal.ROUND_HALF_UP);
                    if (weightPerRep.compareTo(BigDecimal.valueOf(1000)) > 0) {
                        context.buildConstraintViolationWithTemplate("Weight per rep seems unrealistic (over 1000)")
                                .addPropertyNode("exercises[" + i + "].weight")
                                .addConstraintViolation();
                        isValid = false;
                    }
                }

                // Validate reasonable volume per exercise
                if (exercise.getWeight() != null && exercise.getReps() != null && exercise.getSets() != null) {
                    BigDecimal exerciseVolume = exercise.getWeight()
                            .multiply(BigDecimal.valueOf(exercise.getReps()))
                            .multiply(BigDecimal.valueOf(exercise.getSets()));

                    if (exerciseVolume.compareTo(BigDecimal.valueOf(100000)) > 0) {
                        context.buildConstraintViolationWithTemplate("Exercise volume seems unrealistic (over 100,000)")
                                .addPropertyNode("exercises[" + i + "]")
                                .addConstraintViolation();
                        isValid = false;
                    }
                }
            }
        }

        // Validate total volume matches calculated volume (if provided)
        if (totalVolume != null && exercises != null && !exercises.isEmpty()) {
            BigDecimal calculatedVolume = calculateTotalVolume(exercises);
            BigDecimal tolerance = calculatedVolume.multiply(BigDecimal.valueOf(0.05)); // 5% tolerance

            if (totalVolume.subtract(calculatedVolume).abs().compareTo(tolerance) > 0) {
                context.buildConstraintViolationWithTemplate(
                        "Total volume doesn't match calculated volume from exercises")
                        .addPropertyNode("totalVolume")
                        .addConstraintViolation();
                isValid = false;
            }
        }

        // Validate reasonable duration-to-exercise ratio
        if (durationMinutes != null && exercises != null) {
            int minExpectedDuration = exercises.size() * 2; // At least 2 minutes per exercise
            int maxExpectedDuration = exercises.size() * 30; // At most 30 minutes per exercise

            if (durationMinutes < minExpectedDuration) {
                context.buildConstraintViolationWithTemplate("Workout duration seems too short for number of exercises")
                        .addPropertyNode("durationMinutes")
                        .addConstraintViolation();
                isValid = false;
            } else if (durationMinutes > maxExpectedDuration) {
                context.buildConstraintViolationWithTemplate("Workout duration seems too long for number of exercises")
                        .addPropertyNode("durationMinutes")
                        .addConstraintViolation();
                isValid = false;
            }
        }

        return isValid;
    }

    private BigDecimal calculateTotalVolume(List<WorkoutExerciseRequest> exercises) {
        return exercises.stream()
                .filter(exercise -> exercise.getWeight() != null && exercise.getReps() != null
                        && exercise.getSets() != null)
                .map(exercise -> exercise.getWeight()
                        .multiply(BigDecimal.valueOf(exercise.getReps()))
                        .multiply(BigDecimal.valueOf(exercise.getSets())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}