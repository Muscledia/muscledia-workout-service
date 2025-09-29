package com.muscledia.workout_service.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum representing the status of a workout session
 */
@Schema(description = "Current status of a workout session")
public enum WorkoutStatus {
    @Schema(description = "Workout is planned but not yet started")
    PLANNED("PLANNED"),

    @Schema(description = "Workout is currently in progress")
    IN_PROGRESS("IN_PROGRESS"),

    @Schema(description = "Workout has been completed successfully")
    COMPLETED("COMPLETED"),

    @Schema(description = "Workout was cancelled before completion")
    CANCELLED("CANCELLED");

    private final String value;

    WorkoutStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WorkoutStatus fromValue(String value) {
        for (WorkoutStatus status : WorkoutStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid WorkoutStatus value: " + value);
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Check if the status represents an active workout
     */
    public boolean isActive() {
        return this == IN_PROGRESS;
    }

    /**
     * Check if the status represents a completed workout
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * Check if the status represents a finished workout (completed or cancelled)
     */
    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Check if the workout can be modified
     */
    public boolean canBeModified() {
        return this == PLANNED || this == IN_PROGRESS;
    }

    /**
     * Get next valid status transitions
     */
    public WorkoutStatus[] getValidTransitions() {
        return switch (this) {
            case PLANNED -> new WorkoutStatus[]{IN_PROGRESS, CANCELLED};
            case IN_PROGRESS -> new WorkoutStatus[]{COMPLETED, CANCELLED};
            case COMPLETED, CANCELLED -> new WorkoutStatus[]{}; // No further transitions
        };
    }

    /**
     * Check if transition to another status is valid
     */
    public boolean canTransitionTo(WorkoutStatus newStatus) {
        WorkoutStatus[] validTransitions = getValidTransitions();
        for (WorkoutStatus validTransition : validTransitions) {
            if (validTransition == newStatus) {
                return true;
            }
        }
        return false;
    }
}
