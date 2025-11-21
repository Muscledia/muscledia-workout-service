package com.muscledia.workout_service.service.migration;


import com.mongodb.client.result.UpdateResult;
import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutSet;
import com.muscledia.workout_service.model.enums.SetType;
import com.muscledia.workout_service.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * One-time migration service to convert old boolean fields to SetType enum
 *
 * MIGRATION STRATEGY:
 * 1. Find all documents with legacy fields (warmUp, failure, dropSet)
 * 2. Determine SetType based on boolean values
 * 3. Set the setType field
 * 4. Optionally remove old fields (unset them)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SetTypeMigrationService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final WorkoutRepository workoutRepository;

    /**
     * Run migration on application startup
     * COMMENT OUT @EventListener AFTER FIRST SUCCESSFUL RUN
     */
    // @EventListener(ApplicationReadyEvent.class)
    public void migrateSetTypesOnStartup() {
        log.info("Starting SetType migration...");

        migrateSetTypes()
                .doOnSuccess(count -> log.info("✅ Successfully migrated {} workout documents", count))
                .doOnError(error -> log.error("❌ Migration failed: {}", error.getMessage(), error))
                .subscribe();
    }

    /**
     * Main migration method - processes all workouts
     */
    public Mono<Long> migrateSetTypes() {
        return workoutRepository.findAll()
                .flatMap(this::migrateWorkout)
                .count()
                .doOnSubscribe(s -> log.info("🔄 Beginning SetType migration"))
                .doOnSuccess(count -> log.info("✅ Migration completed: {} workouts processed", count));
    }

    /**
     * Migrate a single workout document
     */
    private Mono<Workout> migrateWorkout(Workout workout) {
        boolean needsMigration = false;

        // Check if any sets need migration
        for (var exercise : workout.getExercises()) {
            for (var set : exercise.getSets()) {
                if (needsSetMigration(set)) {
                    needsMigration = true;
                    setDefaultSetType(set);
                }
            }
        }

        if (needsMigration) {
            log.debug("Migrating workout: {}", workout.getId());
            return workoutRepository.save(workout);
        }

        return Mono.just(workout);
    }

    /**
     * Check if a set needs migration (has null setType)
     */
    private boolean needsSetMigration(WorkoutSet set) {
        return set.getSetType() == null;
    }

    /**
     * Set default setType to NORMAL for sets without one
     */
    private void setDefaultSetType(WorkoutSet set) {
        if (set.getSetType() == null) {
            set.setSetType(SetType.NORMAL);
        }
    }

    /**
     * Alternative: Direct MongoDB update approach
     * This uses MongoDB update operations to set default values
     */
    public Mono<Long> migrateSetTypesDirectly() {
        Query query = new Query();

        // Find documents where exercises.sets.set_type doesn't exist
        query.addCriteria(Criteria.where("exercises.sets.set_type").exists(false));

        Update update = new Update();

        // Set default setType to NORMAL for all sets without it
        update.set("exercises.$[].sets.$[].set_type", "NORMAL");

        return mongoTemplate.updateMulti(query, update, Workout.class)
                .map(updateResult -> updateResult.getModifiedCount())
                .doOnSuccess(count -> log.info("✅ Directly updated {} documents", count));
    }

    /**
     * ADVANCED: Migrate based on old boolean fields
     * This reads the raw MongoDB documents to access old fields
     */
    public Mono<Void> migrateFromBooleanFields() {
        return mongoTemplate.getCollection("workouts")
                .flatMapMany(collection ->
                        Flux.from(collection.find())
                )
                .flatMap(document -> {
                    boolean modified = false;

                    // Get exercises array
                    var exercises = (java.util.List<?>) document.get("exercises");
                    if (exercises == null) return Mono.empty();

                    for (Object exerciseObj : exercises) {
                        var exercise = (org.bson.Document) exerciseObj;
                        var sets = (java.util.List<?>) exercise.get("sets");
                        if (sets == null) continue;

                        for (Object setObj : sets) {
                            var set = (org.bson.Document) setObj;

                            // Only migrate if setType doesn't exist
                            if (set.get("set_type") == null) {
                                String setType = determineSetTypeFromBooleans(set);
                                set.put("set_type", setType);
                                modified = true;
                            }
                        }
                    }

                    if (modified) {
                        Query query = new Query(Criteria.where("_id").is(document.get("_id")));
                        Update update = new Update();
                        update.set("exercises", exercises);
                        return mongoTemplate.updateFirst(query, update, "workouts").then();
                    }

                    return Mono.empty();
                })
                .then()
                .doOnSuccess(v -> log.info("✅ Boolean field migration completed"));
    }

    /**
     * Determine SetType from old boolean fields in MongoDB document
     */
    private String determineSetTypeFromBooleans(org.bson.Document set) {
        Boolean warmUp = set.getBoolean("warm_up");
        Boolean failure = set.getBoolean("failure");
        Boolean dropSet = set.getBoolean("drop_set");

        if (Boolean.TRUE.equals(warmUp)) {
            return "WARMUP";
        } else if (Boolean.TRUE.equals(failure)) {
            return "FAILURE";
        } else if (Boolean.TRUE.equals(dropSet)) {
            return "DROP";
        } else {
            return "NORMAL";
        }
    }

    /**
     * CLEANUP: Remove old boolean fields after migration
     * Only run this AFTER confirming migration was successful
     * THIS IS IRREVERSIBLE - make sure you have backups!
     */
    public Mono<Long> removeOldBooleanFields() {
        Query query = new Query();

        Update update = new Update();
        update.unset("exercises.$[].sets.$[].warm_up");
        update.unset("exercises.$[].sets.$[].failure");
        update.unset("exercises.$[].sets.$[].drop_set");

        return mongoTemplate.updateMulti(query, update, Workout.class)
                .map(updateResult -> updateResult.getModifiedCount())
                .doOnSuccess(count -> log.warn("⚠️ Removed old fields from {} documents (IRREVERSIBLE)", count));
    }
}
