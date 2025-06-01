package com.muscledia.workout_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.muscledia.workout_service.model.MuscleGroup;

@Repository
public interface MuscleGroupRepository extends MongoRepository<MuscleGroup, String> {
    Optional<MuscleGroup> findByName(String name);

    Optional<MuscleGroup> findByLatinName(String latinName);

    List<MuscleGroup> findByNameContainingIgnoreCase(String partialName);

    List<MuscleGroup> findByLatinNameContainingIgnoreCase(String partialLatinName);

    @Query("{'name': {'$in': ?0}}")
    List<MuscleGroup> findByNameIn(List<String> names);

    @Query("{'$or': [{'name': {'$regex': ?0, '$options': 'i'}}, {'latinName': {'$regex': ?0, '$options': 'i'}}]}")
    List<MuscleGroup> searchByNameOrLatinName(String searchTerm);

    List<MuscleGroup> findByDescriptionIsNotNull();

    List<MuscleGroup> findByDescriptionContainingIgnoreCase(String searchTerm);

    boolean existsByNameIgnoreCase(String name);
}
