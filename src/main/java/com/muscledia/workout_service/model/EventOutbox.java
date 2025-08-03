package com.muscledia.workout_service.model;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscledia.workout_service.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for storing events that need to be published to Kafka from the Workout Service.
 * Implements the Transactional Outbox Pattern for atomic event publishing.
 *
 * This ensures that events are published atomically with database transactions,
 * preventing lost events and maintaining data consistency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@Document(collection = "workout_event_outbox") // Consider a distinct collection name if sharing DB
public class EventOutbox {
    @Id
    private String id;

    /**
     * The original event ID from the domain event
     */
    @Indexed
    private String eventId;

    /**
     * Type of event for routing
     */
    @Indexed
    private String eventType;

    /**
     * Kafka topic where this event should be published
     */
    private String topic;

    /**
     * Kafka message key (usually userId)
     */
    private String messageKey;

    /**
     * JSON payload of the event
     */
    private String payload; // Stores the JSON string of the event

    /**
     * Current status of the event
     */
    @Indexed
    private EventStatus status;

    /**
     * User ID associated with this event
     */
    @Indexed
    private Long userId;

    /**
     * Number of processing attempts
     */
    @Builder.Default
    private Integer attemptCount = 0;

    /**
     * Maximum number of retry attempts
     */
    @Builder.Default
    private Integer maxAttempts = 3;

    /**
     * Error message if processing failed
     */
    private String errorMessage;

    /**
     * When the event was created
     */
    @CreatedDate
    @Indexed
    private Instant createdAt;

    /**
     * When the event was last updated
     */
    @LastModifiedDate
    private Instant updatedAt;

    /**
     * When the event was successfully published
     */
    private Instant publishedAt;

    /**
     * Next retry time for failed events
     */
    @Indexed
    private Instant nextRetryAt;

    /**
     * Event status enum
     */
    public enum EventStatus {
        PENDING,
        PROCESSING,
        PUBLISHED,
        FAILED,
        DEAD_LETTER
    }

    /**
     * Convenience constructor to create an outbox entry from a BaseEvent
     * using an ObjectMapper to serialize the payload.
     */
    public EventOutbox(String topic, String messageKey, BaseEvent event, ObjectMapper objectMapper) throws JsonProcessingException {
        this.id = event.getEventId(); // Use eventId as outbox ID for idempotency/tracking
        this.eventId = event.getEventId();
        this.eventType = event.getEventType();
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = objectMapper.writeValueAsString(event); // Serialize event to JSON string
        this.status = EventStatus.PENDING;
        this.userId = event.getUserId();
        this.attemptCount = 0;
        this.maxAttempts = 3; // Default max attempts
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }


    /**
     * Check if this event can be retried
     */
    public boolean canRetry() {
        return status == EventStatus.FAILED &&
                attemptCount < maxAttempts &&
                (nextRetryAt == null || nextRetryAt.isBefore(Instant.now()));
    }

    /**
     * Increment attempt count and set next retry time
     */
    public void incrementAttempt() {
        this.attemptCount++;
        // Exponential backoff: 1min, 5min, 15min
        long backoffMinutes = (long) Math.pow(5, attemptCount - 1);
        this.nextRetryAt = Instant.now().plusSeconds(backoffMinutes * 60);
        this.updatedAt = Instant.now();
    }

    /**
     * Mark as successfully published
     */
    public void markAsPublished() {
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark as failed with error message
     */
    public void markAsFailed(String errorMessage) {
        this.status = EventStatus.FAILED;
        this.errorMessage = errorMessage;
        incrementAttempt(); // Increment attempt before checking maxAttempts

        if (this.attemptCount >= this.maxAttempts) {
            this.status = EventStatus.DEAD_LETTER;
            log.warn("Event {} (type: {}) moved to DEAD_LETTER after {} attempts.", this.eventId, this.eventType, this.attemptCount);
        }
        this.updatedAt = Instant.now();
    }
}
