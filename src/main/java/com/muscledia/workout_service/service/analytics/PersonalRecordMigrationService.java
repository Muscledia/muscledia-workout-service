package com.muscledia.workout_service.service.analytics;

import com.mongodb.client.result.UpdateResult;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.repository.analytics.PersonalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordMigrationService {

    private final PersonalRecordRepository personalRecordRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Update existing PRs with proper exercise names for a specific user
     */
    public Mono<Long> fixExerciseNamesForUser(Long userId) {
        Map<String, String> exerciseNameMap = Map.of(
                "72CFFAD5", "Romanian Deadlift (Dumbbell)",
                "3D0C7C75", "Goblet Squat",
                "6DA40660", "Standing Calf Raise (Dumbbell)",
                "B5D3A742", "Bulgarian Split Squat",
                "55E6546F", "Barbell Squat",
                "6A6C31A5", "Deadlift",
                "ABEC557F", "Bench Press"
        );

        return personalRecordRepository.findByUserId(userId)
                .filter(pr -> pr.getExerciseName().startsWith("Exercise "))
                .flatMap(pr -> {
                    String properName = exerciseNameMap.get(pr.getExerciseId());
                    if (properName != null) {
                        log.info("Updating PR {} from '{}' to '{}' for user {}",
                                pr.getId(), pr.getExerciseName(), properName, userId);

                        Query query = Query.query(Criteria.where("id").is(pr.getId()));
                        Update update = new Update().set("exerciseName", properName);

                        return mongoTemplate.updateFirst(query, update, PersonalRecord.class)
                                .doOnSuccess(result -> log.info("Updated PR {}: {}", pr.getId(), result.getModifiedCount()));
                    } else {
                        log.warn("No mapping found for exercise ID: {} for user {}", pr.getExerciseId(), userId);
                        return mongoTemplate.updateFirst(
                                Query.query(Criteria.where("id").is("non-existent")),
                                new Update(),
                                PersonalRecord.class
                        );
                    }
                })
                .map(result -> result.getModifiedCount())
                .reduce(0L, Long::sum)
                .doOnSuccess(totalUpdated -> log.info("Migration complete for user {}: {} PRs updated", userId, totalUpdated));
    }
}
