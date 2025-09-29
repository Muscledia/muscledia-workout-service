package com.muscledia.workout_service.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.muscledia.workout_service.model.MuscleGroup;

@Repository
public interface MuscleGroupRepository extends ReactiveMongoRepository<MuscleGroup, String> {
    Mono<MuscleGroup> findByName(String name);

    /**
     * Safe version of findByName that takes the first result if multiple exist
     * Used to handle potential duplicate scenarios gracefully
     */
    @Query("{'name': ?0}")
    Mono<MuscleGroup> findFirstByName(String name);

    Mono<MuscleGroup> findByLatinName(String latinName);

    Flux<MuscleGroup> findByNameContainingIgnoreCase(String partialName);

    Flux<MuscleGroup> findByLatinNameContainingIgnoreCase(String partialLatinName);

    @Query("{'name': {'$in': ?0}}")
    Flux<MuscleGroup> findByNameIn(List<String> names);

    @Query("{'$or': [{'name': {'$regex': ?0, '$options': 'i'}}, {'latinName': {'$regex': ?0, '$options': 'i'}}]}")
    Flux<MuscleGroup> searchByNameOrLatinName(String searchTerm);

    Flux<MuscleGroup> findByDescriptionIsNotNull();

    Flux<MuscleGroup> findByDescriptionContainingIgnoreCase(String searchTerm);

    Mono<Boolean> existsByNameIgnoreCase(String name);
}
