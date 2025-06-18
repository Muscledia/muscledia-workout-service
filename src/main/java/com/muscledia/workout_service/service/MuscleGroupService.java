package com.muscledia.workout_service.service;

import com.muscledia.workout_service.model.MuscleGroup;
import com.muscledia.workout_service.repository.MuscleGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MuscleGroupService {
    private final MuscleGroupRepository muscleGroupRepository;

    public Mono<MuscleGroup> findById(String id) {
        return muscleGroupRepository.findById(id)
                .doOnNext(muscleGroup -> log.debug("Found muscle group: {}", muscleGroup.getName()))
                .switchIfEmpty(Mono.error(new RuntimeException("Muscle group not found with id: " + id)));
    }

    public Mono<MuscleGroup> findByName(String name) {
        return muscleGroupRepository.findByName(name)
                .doOnNext(muscleGroup -> log.debug("Found muscle group by name: {}", muscleGroup.getName()));
    }

    public Mono<MuscleGroup> findFirstByName(String name) {
        return muscleGroupRepository.findFirstByName(name)
                .doOnNext(muscleGroup -> log.debug("Found first muscle group by name: {}", muscleGroup.getName()));
    }

    public Mono<MuscleGroup> findByLatinName(String latinName) {
        return muscleGroupRepository.findByLatinName(latinName)
                .doOnNext(muscleGroup -> log.debug("Found muscle group by Latin name: {}", muscleGroup.getName()));
    }

    public Flux<MuscleGroup> searchByName(String partialName) {
        return muscleGroupRepository.findByNameContainingIgnoreCase(partialName)
                .doOnComplete(() -> log.debug("Retrieved muscle groups matching name: {}", partialName));
    }

    public Flux<MuscleGroup> searchByLatinName(String partialLatinName) {
        return muscleGroupRepository.findByLatinNameContainingIgnoreCase(partialLatinName)
                .doOnComplete(() -> log.debug("Retrieved muscle groups matching Latin name: {}", partialLatinName));
    }

    public Flux<MuscleGroup> findByNames(List<String> names) {
        return muscleGroupRepository.findByNameIn(names)
                .doOnComplete(() -> log.debug("Retrieved muscle groups for names: {}", names));
    }

    public Flux<MuscleGroup> searchByNameOrLatinName(String searchTerm) {
        return muscleGroupRepository.searchByNameOrLatinName(searchTerm)
                .doOnComplete(() -> log.debug("Retrieved muscle groups matching name or Latin name: {}", searchTerm));
    }

    public Flux<MuscleGroup> findWithDescriptions() {
        return muscleGroupRepository.findByDescriptionIsNotNull()
                .doOnComplete(() -> log.debug("Retrieved muscle groups with descriptions"));
    }

    public Flux<MuscleGroup> searchByDescription(String searchTerm) {
        return muscleGroupRepository.findByDescriptionContainingIgnoreCase(searchTerm)
                .doOnComplete(() -> log.debug("Retrieved muscle groups matching description: {}", searchTerm));
    }

    public Mono<Boolean> existsByName(String name) {
        return muscleGroupRepository.existsByNameIgnoreCase(name)
                .doOnSuccess(
                        exists -> log.debug("Checked existence of muscle group name: {}, exists: {}", name, exists));
    }

    public Mono<MuscleGroup> save(MuscleGroup muscleGroup) {
        if (muscleGroup.getCreatedAt() == null) {
            muscleGroup.setCreatedAt(LocalDateTime.now());
        }
        return muscleGroupRepository.save(muscleGroup)
                .doOnSuccess(saved -> log.debug("Saved muscle group: {}", saved.getName()));
    }

    public Mono<Void> deleteById(String id) {
        return muscleGroupRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("Deleted muscle group with id: {}", id));
    }

    public Flux<MuscleGroup> findAll() {
        return muscleGroupRepository.findAll()
                .doOnComplete(() -> log.debug("Retrieved all muscle groups"));
    }
}