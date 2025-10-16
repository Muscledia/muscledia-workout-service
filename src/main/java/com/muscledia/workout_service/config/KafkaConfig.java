package com.muscledia.workout_service.config;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

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

        // ✅ CRITICAL: Connection and timeout settings
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60000);

        // ✅ CRITICAL: Connection setup timeouts
        props.put("socket.connection.setup.timeout.ms", 10000);
        props.put("socket.connection.setup.timeout.max.ms", 30000);

        // Performance settings
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

        // Reliability Configuration (NO TRANSACTIONS)
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);


        // JSON Serialization Configuration: IMPORTANT for cross-service type mapping
        // This mapping ensures that when WorkoutCompletedEvent is serialized by workout-service,
        // it includes type info that gamification-service's JsonDeserializer can understand.
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        props.put(JsonSerializer.TYPE_MAPPINGS, getTypeMapping());


        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);

        log.info("✅ KAFKA PRODUCER FACTORY CREATED - transactionCapable: {}", factory.transactionCapable());
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        log.info("🔧 CREATING KAFKA TEMPLATE");
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());

        log.info("✅ KAFKA TEMPLATE CREATED");
        return template;
    }

    /**
     * UPDATED: Define type mappings for JSON serialization including PersonalRecordEvent.
     * The keys ('workout', 'pr') MUST precisely match the `__TypeId__` values
     * expected by the consuming service (`Gamification_service`) as defined in its
     * KafkaConfig's `getTypeMapping()` method.
     */
    private String getTypeMapping() {
        // Include all types that THIS service will publish.
        return "workout:com.muscledia.workout_service.event.WorkoutCompletedEvent," +
                "pr:com.muscledia.workout_service.event.PersonalRecordEvent";
        //"exercise:com.muscledia.workout_service.event.ExerciseCompletedEvent";
    }


}