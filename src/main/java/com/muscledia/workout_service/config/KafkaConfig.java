package com.muscledia.workout_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for event-driven workout processing.
 */
@Configuration
@EnableKafka

public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.transaction-id-prefix:workout-service-tx-}")
    private String transactionIdPrefix;

    // ===============================
    // PRODUCER CONFIGURATION
    // ===============================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Basic Configuration
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Performance Optimizations
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB batches
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait up to 10ms for batching
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

        // Reliability Configuration
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader acknowledgment
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        // Idempotent producer for exactly-once delivery semantics (recommended)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Transactional Producer (if using Spring's Kafka transaction manager)
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionIdPrefix);


        // JSON Serialization Configuration: IMPORTANT for cross-service type mapping
        // This mapping ensures that when WorkoutCompletedEvent is serialized by workout-service,
        // it includes type info that gamification-service's JsonDeserializer can understand.
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        props.put(JsonSerializer.TYPE_MAPPINGS, getTypeMapping());

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Define type mappings for JSON serialization.
     * The keys ('workout', 'exercise') MUST precisely match the `__TypeId__` values
     * expected by the consuming service (`Gamification_service`) as defined in its
     * KafkaConfig's `getTypeMapping()` method.
     */
    private String getTypeMapping() {
        // Only include types that THIS service will publish.
        // The keys "WORKOUT_COMPLETED" and "EXERCISE_COMPLETED" must match
        // what Gamification_service's BaseEvent @JsonSubTypes expects.
        return "workout:com.muscledia.workout_service.event.WorkoutCompletedEvent," +
                "exercise:com.muscledia.workout_service.event.ExerciseCompletedEvent";
    }


}