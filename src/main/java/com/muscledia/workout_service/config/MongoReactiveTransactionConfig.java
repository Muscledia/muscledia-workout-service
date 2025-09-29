package com.muscledia.workout_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@EnableReactiveMongoRepositories(basePackages = "com.muscledia.workout_service.repository")
@EnableReactiveMongoAuditing // Only if you use Spring Data Auditing features like @CreatedDate
@Configuration
public class MongoReactiveTransactionConfig {
    /**
     * Creates a ReactiveMongoTransactionManager bean.
     * This is crucial for enabling reactive transactions with MongoDB.
     * Requires a ReactiveMongoDatabaseFactory.
     */
    @Bean
    public ReactiveMongoTransactionManager reactiveMongoTransactionManager(ReactiveMongoDatabaseFactory reactiveMongoDatabaseFactory) {
        return new ReactiveMongoTransactionManager(reactiveMongoDatabaseFactory);
    }

    /**
     * Exposes TransactionalOperator as a bean, making it available for injection.
     * It uses the ReactiveTransactionManager (which we defined above).
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager reactiveTransactionManager) {
        return TransactionalOperator.create(reactiveTransactionManager);
    }

    // You likely don't need to define ReactiveMongoDatabaseFactory or MongoClient
    // if Spring Boot auto-configures it correctly from your application.yaml.
    // However, if you *were* to define them manually, they would look something like this:
    /*
    @Bean
    public ReactiveMongoDatabaseFactory reactiveMongoDatabaseFactory(MongoClient mongoClient) {
        // Replace "your_database_name" with your actual database name
        return new SimpleReactiveMongoDatabaseFactory(mongoClient, "muscledia_workouts");
    }

    @Bean
    public MongoClient mongoClient() {
        // Spring Boot usually auto-configures this from spring.data.mongodb.uri
        // You generally don't need to define this if auto-config is used.
        return MongoClients.create("mongodb+srv://mongo:NSX2O96ElPhiLPMk@cluster0.dcypj6u.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0&connectTimeoutMS=20000&socketTimeoutMS=20000&maxIdleTimeMS=120000&maxLifeTimeMS=120000&authSource=muscledia_workouts");
    }
    */
}
