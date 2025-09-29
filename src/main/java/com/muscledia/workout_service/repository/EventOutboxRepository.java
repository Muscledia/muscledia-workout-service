package com.muscledia.workout_service.repository;

import com.muscledia.workout_service.model.EventOutbox;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Repository for managing event outbox entries specific to the Workout Service.
 * Supports the Transactional Outbox Pattern for reliable event publishing.
 */
@Repository
public interface EventOutboxRepository extends ReactiveMongoRepository<EventOutbox, String> {

    /**
     * Find events that are ready to be published
     */
    @Query("{ 'status': 'PENDING' }")
    Flux<EventOutbox> findPendingEvents();

    /**
     * Find failed events that are ready for retry
     */
    @Query("{ 'status': 'FAILED', " +
            "'attemptCount': { $lt: ?0 }, " + // ?0 maps to maxAttempts
            "$or: [ " +
            "  { 'nextRetryAt': { $exists: false } }, " +
            "  { 'nextRetryAt': { $lt: ?1 } } " + // ?1 maps to Instant now
            "] }")
    Flux<EventOutbox> findRetryableFailedEvents(Integer maxAttempts, Instant now);

    /**
     * Find events by status
     */
    Flux<EventOutbox> findByStatus(EventOutbox.EventStatus status);

    /**
     * Find events by user ID and status
     */
    Flux<EventOutbox> findByUserIdAndStatus(Long userId, EventOutbox.EventStatus status);

    /**
     * Find event by original event ID
     */
    Mono<EventOutbox> findByEventId(String eventId);

    /**
     * Count events by status
     */
    Mono<Long> countByStatus(EventOutbox.EventStatus status);

    /**
     * Find old processed events for cleanup (older than specified time)
     */
    @Query("{ 'status': 'PUBLISHED', 'publishedAt': { $lt: ?0 } }")
    Flux<EventOutbox> findOldPublishedEvents(Instant olderThan);

    /**
     * Find dead letter events for manual review
     */
    @Query("{ 'status': 'DEAD_LETTER' }")
    Flux<EventOutbox> findDeadLetterEvents();

    /**
     * Find events by topic for monitoring
     */
    Flux<EventOutbox> findByTopic(String topic);

    /**
     * Delete old published events (cleanup)
     */
    Mono<Void> deleteByStatusAndPublishedAtBefore(EventOutbox.EventStatus status, Instant before);

}
