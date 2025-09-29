package com.muscledia.workout_service.migration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * One-time migration script to move data from MongoDB Atlas to Docker MongoDB
 * Run with: mvn spring-boot:run -Dspring-boot.run.profiles=migration
 */
@Component
@Profile("migration")
public class AtlasToDockerMigration implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(AtlasToDockerMigration.class);

    // Atlas connection string
    private static final String ATLAS_URI =
            "mongodb+srv://mongo:NSX2O96ElPhiLPMk@cluster0.dcypj6u.mongodb.net/" +
                    "muscledia_workouts?retryWrites=true&w=majority&appName=Cluster0";

    // Docker MongoDB connection string
    private static final String DOCKER_URI =
            "mongodb://admin:secure_mongo_password_123@localhost:27017/" +
                    "muscledia_workouts?authSource=admin";

    private static final String DATABASE_NAME = "muscledia_workouts";

    @Override
    public void run(String... args) {
        logger.info("Starting migration from Atlas to Docker MongoDB...");

        try (MongoClient atlasClient = MongoClients.create(ATLAS_URI);
             MongoClient dockerClient = MongoClients.create(DOCKER_URI)) {

            MongoDatabase atlasDb = atlasClient.getDatabase(DATABASE_NAME);
            MongoDatabase dockerDb = dockerClient.getDatabase(DATABASE_NAME);

            // Migrate collections
            migrateCollection(atlasDb, dockerDb, "routine_folders");
            migrateCollection(atlasDb, dockerDb, "workout_plans");
            migrateCollection(atlasDb, dockerDb, "exercises");
            migrateCollection(atlasDb, dockerDb, "muscle_groups");
            migrateCollection(atlasDb, dockerDb, "workouts");
            migrateCollection(atlasDb, dockerDb, "personal_records");

            logger.info("✅ Migration completed successfully!");

            // Exit the application after migration
            System.exit(0);

        } catch (Exception e) {
            logger.error("❌ Migration failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void migrateCollection(MongoDatabase source, MongoDatabase target, String collectionName) {
        try {
            logger.info("📦 Migrating collection: {}", collectionName);

            MongoCollection<Document> sourceCollection = source.getCollection(collectionName);
            MongoCollection<Document> targetCollection = target.getCollection(collectionName);

            // Get all documents - using forEach to avoid FindPublisher issue
            List<org.bson.Document> documents = new ArrayList<>();
            source.getCollection(collectionName).find().forEach(documents::add);

            if (documents.isEmpty()) {
                logger.warn("⚠️  No documents found in {}", collectionName);
                return;
            }

            // Optional: Clear target collection first (uncomment if needed)
            // targetCollection.deleteMany(new Document());
            // logger.info("🗑️  Cleared existing data in target {}", collectionName);

            // Insert documents in batches
            int batchSize = 1000;
            int totalDocs = documents.size();
            int processed = 0;

            for (int i = 0; i < totalDocs; i += batchSize) {
                int end = Math.min(i + batchSize, totalDocs);
                List<Document> batch = documents.subList(i, end);

                try {
                    targetCollection.insertMany(batch);
                    processed += batch.size();
                    logger.info("   ➜ Inserted {}/{} documents", processed, totalDocs);
                } catch (Exception e) {
                    logger.error("   ❌ Error inserting batch: {}", e.getMessage());
                    // Try inserting one by one
                    for (Document doc : batch) {
                        try {
                            targetCollection.insertOne(doc);
                            processed++;
                        } catch (Exception ex) {
                            logger.error("   ❌ Failed to insert document: {}", doc.get("_id"));
                        }
                    }
                }
            }

            logger.info("✅ Successfully migrated {} documents from {}", processed, collectionName);

        } catch (Exception e) {
            logger.error("❌ Error migrating collection {}: {}", collectionName, e.getMessage(), e);
        }
    }
}
