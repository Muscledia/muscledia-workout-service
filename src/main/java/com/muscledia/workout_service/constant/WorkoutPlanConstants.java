package com.muscledia.workout_service.constant;


/**
 * Constants for workout plan configuration
 *
 * SOLID: Single source of truth for default values
 */
public class WorkoutPlanConstants {

    private WorkoutPlanConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // Default set configuration
    public static final int DEFAULT_NUMBER_OF_SETS = 3;
    public static final int DEFAULT_REPS_PER_SET = 10;
    public static final int DEFAULT_REST_SECONDS = 90;

    // Pagination defaults
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_PAGE_NUMBER = 0;
    public static final int MAX_PAGE_SIZE = 100;

    // Exercise browsing
    public static final int BROWSE_EXERCISES_PAGE_SIZE = 20;
}
