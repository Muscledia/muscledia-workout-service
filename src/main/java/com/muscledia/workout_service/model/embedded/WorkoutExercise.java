package com.muscledia.workout_service.model.embedded;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;
import java.math.BigDecimal;

@Data
public class WorkoutExercise {
    @Field("exercise_id")
    @NotBlank(message = "Exercise ID is required")
    private String exerciseId;

    @NotNull(message = "Number of sets is required")
    @Min(value = 1, message = "Must perform at least 1 set")
    @Max(value = 100, message = "Cannot exceed 100 sets per exercise")
    private Integer sets;

    @NotNull(message = "Number of reps is required")
    @Min(value = 1, message = "Must perform at least 1 rep")
    @Max(value = 1000, message = "Cannot exceed 1000 reps per set")
    private Integer reps;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.0", message = "Weight cannot be negative")
    @DecimalMax(value = "9999.99", message = "Weight cannot exceed 9999.99")
    @Digits(integer = 4, fraction = 2, message = "Weight must have at most 4 integer digits and 2 decimal places")
    private BigDecimal weight;

    @Field("exercise_order")
    @Min(value = 1, message = "Exercise order must be at least 1")
    @Max(value = 50, message = "Exercise order cannot exceed 50")
    private Integer order;

    @Size(max = 500, message = "Exercise notes cannot exceed 500 characters")
    private String notes;
}