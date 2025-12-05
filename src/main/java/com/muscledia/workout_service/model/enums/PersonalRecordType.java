package com.muscledia.workout_service.model.enums;

import lombok.Getter;

@Getter
public enum PersonalRecordType {

    MAX_WEIGHT("MAX_WEIGHT", "Maximum Weight", "kg"),
    MAX_REPS("MAX_REPS", "Maximum Reps", "reps"),
    MAX_VOLUME("MAX_VOLUME", "Maximum Volume", "kg"),
    ESTIMATED_1RM("ESTIMATED_1RM", "Estimated 1RM", "kg");

    private final String code;
    private final String displayName;
    private final String unit;

    PersonalRecordType(String code, String displayName, String unit) {
        this.code = code;
        this.displayName = displayName;
        this.unit = unit;
    }

    /**
     * Get enum from string code
     */
    public static PersonalRecordType fromCode(String code) {
        for (PersonalRecordType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PR type: " + code);
    }

    /**
     * Check if this PR type is weight-based
     */
    public boolean isWeightBased() {
        return this == MAX_WEIGHT || this == ESTIMATED_1RM;
    }

    /**
     * Check if this PR type is volume-based
     */
    public boolean isVolumeBased() {
        return this == MAX_VOLUME;
    }

    /**
     * Check if this PR type is reps-based
     */
    public boolean isRepsBased() {
        return this == MAX_REPS;
    }
}
