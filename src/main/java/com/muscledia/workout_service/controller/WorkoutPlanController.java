package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.WorkoutPlan;
import com.muscledia.workout_service.service.WorkoutPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/workout-plans")
@RequiredArgsConstructor
@Slf4j
public class WorkoutPlanController {
    private final WorkoutPlanService workoutPlanService;

    // PUBLIC EXPLORATION ENDPOINTS (No authentication required)

    @GetMapping("/public/{id}")
    public Mono<ResponseEntity<WorkoutPlan>> getPublicWorkoutPlan(@PathVariable String id) {
        return workoutPlanService.findById(id)
                .filter(plan -> plan.getIsPublic()) // Only return if public
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(response -> log.debug("Retrieved public workout plan with id: {}", id));
    }

    @GetMapping("/public")
    public Flux<WorkoutPlan> getPublicWorkoutPlans() {
        return workoutPlanService.findPublicWorkoutPlans();
    }

    @GetMapping("/public/search")
    public Flux<WorkoutPlan> searchPublicWorkoutPlans(@RequestParam String term) {
        return workoutPlanService.searchPublicWorkouts(term);
    }

    @GetMapping("/public/popular")
    public Flux<WorkoutPlan> getPopularWorkoutPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return workoutPlanService.findPopularWorkoutPlans(PageRequest.of(page, size));
    }

    @GetMapping("/public/recent")
    public Flux<WorkoutPlan> getRecentWorkoutPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return workoutPlanService.findRecentWorkoutPlans(PageRequest.of(page, size));
    }

    @GetMapping("/public/muscle-group/{muscleGroup}")
    public Flux<WorkoutPlan> getPublicWorkoutPlansByMuscleGroup(@PathVariable String muscleGroup) {
        return workoutPlanService.findByTargetMuscleGroup(muscleGroup)
                .filter(plan -> plan.getIsPublic()); // Only return public plans
    }

    @GetMapping("/public/equipment/{equipment}")
    public Flux<WorkoutPlan> getPublicWorkoutPlansByEquipment(@PathVariable String equipment) {
        return workoutPlanService.findByRequiredEquipment(equipment)
                .filter(plan -> plan.getIsPublic()); // Only return public plans
    }

    @GetMapping("/public/duration")
    public Flux<WorkoutPlan> getPublicWorkoutPlansByDuration(
            @RequestParam Integer minDuration,
            @RequestParam Integer maxDuration) {
        return workoutPlanService.findByDurationRange(minDuration, maxDuration)
                .filter(plan -> plan.getIsPublic()); // Only return public plans
    }

    // PERSONAL COLLECTION ENDPOINTS (Authentication required)

    @PostMapping("/save/{publicId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkoutPlan> saveToPersonalCollection(
            @PathVariable String publicId,
            @RequestParam Long userId) { // In real app, this would come from JWT token
        return workoutPlanService.saveToPersonalCollection(publicId, userId);
    }

    @GetMapping("/personal")
    public Flux<WorkoutPlan> getPersonalWorkoutPlans(@RequestParam Long userId) {
        return workoutPlanService.findPersonalWorkoutPlans(userId);
    }

    @GetMapping("/my-created")
    public Flux<WorkoutPlan> getMyCreatedWorkoutPlans(@RequestParam Long userId) {
        return workoutPlanService.findByCreator(userId);
    }

    @PostMapping("/personal")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkoutPlan> createPersonalWorkoutPlan(
            @RequestBody WorkoutPlan workoutPlan,
            @RequestParam Long userId) {
        workoutPlan.setIsPublic(false);
        workoutPlan.setCreatedBy(userId);
        return workoutPlanService.save(workoutPlan);
    }

    // ADMIN/SYSTEM ENDPOINTS (For creating public content)

    @PostMapping("/public")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WorkoutPlan> createPublicWorkoutPlan(@RequestBody WorkoutPlan workoutPlan) {
        workoutPlan.setIsPublic(true);
        workoutPlan.setCreatedBy(1L); // System user
        return workoutPlanService.save(workoutPlan);
    }

    // GENERAL ENDPOINTS

    @PutMapping("/{id}")
    public Mono<ResponseEntity<WorkoutPlan>> updateWorkoutPlan(
            @PathVariable String id,
            @RequestBody WorkoutPlan workoutPlan,
            @RequestParam Long userId) {
        workoutPlan.setId(id);
        // Only allow updating if user is the creator
        return workoutPlanService.findById(id)
                .filter(existing -> existing.getCreatedBy().equals(userId))
                .flatMap(existing -> {
                    workoutPlan.setCreatedBy(userId);
                    return workoutPlanService.save(workoutPlan);
                })
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(response -> log.debug("Updated workout plan with id: {}", id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteWorkoutPlan(@PathVariable String id, @RequestParam Long userId) {
        // Only allow deleting if user is the creator
        return workoutPlanService.findById(id)
                .filter(existing -> existing.getCreatedBy().equals(userId))
                .flatMap(existing -> workoutPlanService.deleteById(id))
                .then();
    }
}