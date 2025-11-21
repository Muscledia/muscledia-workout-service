package com.muscledia.workout_service.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
    * Defines the type of set for accurate training intensity tracking and analytics.
    *
    * BUSINESS RULES:
    * - WARMUP sets are excluded from personal record calculations
 * - WARMUP sets are excluded from volume analytics
 * - FAILURE sets indicate training to muscular failure
 * - DROP sets indicate reduced weight continuation
 */
public enum SetType {
    NORMAL("NORMAL"),
    WARMUP("WARMUP"),
    FAILURE("FAILURE"),
    DROP("DROP");

    private final String value;

    SetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SetType fromValue(String value) {
        if (value == null) {
            return NORMAL;
        }

        for (SetType type : SetType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        // Backward compatibility: map old "WORKING" to NORMAL
        if ("WORKING".equalsIgnoreCase(value)) {
            return NORMAL;
        }

        return NORMAL; // Default fallback
    }

    /**
     * Check if this is a working set (counts toward training volume)
     */
    public boolean isWorkingSet() {
        return this == NORMAL || this == FAILURE || this == DROP;
    }

    /**
     * Check if this set counts toward volume calculations
     */
    public boolean countsTowardVolume() {
        return this != WARMUP;
    }

    /**
     * Check if this set counts toward personal record detection
     */
    public boolean countsTowardPersonalRecords() {
        return this == NORMAL || this == FAILURE;
    }
}
