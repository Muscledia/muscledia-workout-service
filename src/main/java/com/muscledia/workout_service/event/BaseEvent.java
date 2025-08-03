package com.muscledia.workout_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all events published by the workout service.
 * Provides common fields and serialization configuration.
 */
@Data
@SuperBuilder(toBuilder = true) // Important for builder pattern inheritance
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WorkoutCompletedEvent.class, name = "WORKOUT_COMPLETED"),
        @JsonSubTypes.Type(value = ExerciseCompletedEvent.class, name = "EXERCISE_COMPLETED") // <--- ADDED THIS
        // Add other event types here if workout-service ever publishes more
})
public abstract class BaseEvent {

    @NotBlank
    @lombok.Builder.Default
    protected String eventId = UUID.randomUUID().toString();

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    @lombok.Builder.Default
    protected Instant timestamp = Instant.now();

    @NotNull
    protected Long userId; // Aligned with Gamification_service's BaseEvent

    @NotBlank
    @lombok.Builder.Default
    protected String source = "workout-service"; // This service is the source

    @NotBlank
    @lombok.Builder.Default
    protected String version = "1.0"; // Event schema version

    /**
     * Constructor for common fields, used by subclasses.
     */
    protected BaseEvent(Long userId, String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.userId = userId;
        // eventType is set by JsonSubTypes and getEventType() abstract method
        this.source = "workout-service";
        this.version = "1.0";
    }

    /**
     * Constructor with all fields for builder/deserialization.
     */
    protected BaseEvent(String eventId, Instant timestamp, Long userId, String source, String version) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.userId = userId;
        this.source = source != null ? source : "workout-service";
        this.version = version != null ? version : "1.0";
    }

    /**
     * Get the event type for routing and processing (must match name in @JsonSubTypes)
     */
    public abstract String getEventType();

    /**
     * Validate event-specific business rules
     */
    public abstract boolean isValid();

    /**
     * Create a copy of this event with updated timestamp (for retries, if needed for source service)
     */
    public abstract BaseEvent withNewTimestamp();
}
