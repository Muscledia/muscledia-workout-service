package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.service.WorkoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
@Slf4j
public class WorkoutController {
    private final WorkoutService workoutService;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Workout>> getWorkoutById(@PathVariable String id) {
        return workoutService.findById(id)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Retrieved workout with id: {}", id));
    }

    @GetMapping("/user/{userId}")
    public Flux<Workout> getWorkoutsByUser(@PathVariable Long userId) {
        return workoutService.findByUser(userId);
    }

    @GetMapping("/user/{userId}/date-range")
    public Flux<Workout> getWorkoutsByUserAndDateRange(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return workoutService.findByUserAndDateRange(userId, start, end);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Workout> createWorkout(@RequestBody Workout workout) {
        return workoutService.save(workout);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Workout>> updateWorkout(
            @PathVariable String id,
            @RequestBody Workout workout) {
        workout.setId(id);
        return workoutService.save(workout)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> log.debug("Updated workout with id: {}", id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteWorkout(@PathVariable String id) {
        return workoutService.deleteById(id);
    }

    @GetMapping
    public Flux<Workout> getAllWorkouts() {
        return workoutService.findAll();
    }
}