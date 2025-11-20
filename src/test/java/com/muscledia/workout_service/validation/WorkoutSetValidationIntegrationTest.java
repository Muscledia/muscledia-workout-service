package com.muscledia.workout_service.validation;

import com.muscledia.workout_service.model.Workout;
import com.muscledia.workout_service.model.embedded.WorkoutExercise;
import com.muscledia.workout_service.model.enums.WorkoutStatus;
import com.muscledia.workout_service.repository.WorkoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("Workout Session Status Validation Tests")
class WorkoutSetValidationIntegrationTest {
    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkoutRepository workoutRepository;

    private String inProgressWorkoutId;
    private String completedWorkoutId;
    private String plannedWorkoutId;
    private String cancelledWorkoutId;
    private Long userId = 1L;
    private String authToken = "Bearer test-token"; // Mock JWT token

    @BeforeEach
    void setUp() {
        workoutRepository.deleteAll().block();

        // Create workouts with different statuses
        Workout inProgressWorkout = createWorkout(WorkoutStatus.IN_PROGRESS);
        Workout completedWorkout = createWorkout(WorkoutStatus.COMPLETED);
        Workout plannedWorkout = createWorkout(WorkoutStatus.PLANNED);
        Workout cancelledWorkout = createWorkout(WorkoutStatus.CANCELLED);

        inProgressWorkoutId = workoutRepository.save(inProgressWorkout).block().getId();
        completedWorkoutId = workoutRepository.save(completedWorkout).block().getId();
        plannedWorkoutId = workoutRepository.save(plannedWorkout).block().getId();
        cancelledWorkoutId = workoutRepository.save(cancelledWorkout).block().getId();
    }

    // ==================== LOG SET TESTS ====================

    @Test
    @DisplayName("Should successfully log set on IN_PROGRESS workout")
    void shouldLogSetOnInProgressWorkout() {
        webTestClient.post()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets", inProgressWorkoutId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "weightKg": 100.0,
                      "reps": 10,
                      "completed": true,
                      "rpe": 8
                    }
                    """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(inProgressWorkoutId);
    }

    @Test
    @DisplayName("Should fail to log set on COMPLETED workout")
    void shouldFailToLogSetOnCompletedWorkout() {
        webTestClient.post()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets", completedWorkoutId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "weightKg": 100.0,
                      "reps": 10,
                      "completed": true
                    }
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(containsString("COMPLETED"))
                .jsonPath("$.message").value(containsString("IN_PROGRESS"))
                .jsonPath("$.details.currentState").isEqualTo("COMPLETED")
                .jsonPath("$.details.requiredState").isEqualTo("IN_PROGRESS")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("Should fail to log set on PLANNED workout")
    void shouldFailToLogSetOnPlannedWorkout() {
        webTestClient.post()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets", plannedWorkoutId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "weightKg": 100.0,
                      "reps": 10,
                      "completed": true
                    }
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(containsString("PLANNED"))
                .jsonPath("$.details.currentState").isEqualTo("PLANNED");
    }

    @Test
    @DisplayName("Should fail to log set on CANCELLED workout")
    void shouldFailToLogSetOnCancelledWorkout() {
        webTestClient.post()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets", cancelledWorkoutId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "weightKg": 100.0,
                      "reps": 10,
                      "completed": true
                    }
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(containsString("CANCELLED"))
                .jsonPath("$.details.currentState").isEqualTo("CANCELLED");
    }

    // ==================== UPDATE SET TESTS ====================

    @Test
    @DisplayName("Should fail to update set on COMPLETED workout")
    void shouldFailToUpdateSetOnCompletedWorkout() {
        webTestClient.put()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets/0", completedWorkoutId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "weightKg": 110.0,
                      "reps": 12,
                      "completed": true
                    }
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(containsString("IN_PROGRESS"))
                .jsonPath("$.details.currentState").isEqualTo("COMPLETED");
    }

    // ==================== DELETE SET TESTS ====================

    @Test
    @DisplayName("Should fail to delete set on COMPLETED workout")
    void shouldFailToDeleteSetOnCompletedWorkout() {
        webTestClient.delete()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets/0", completedWorkoutId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(containsString("IN_PROGRESS"))
                .jsonPath("$.details.currentState").isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should verify error message clarity for user feedback")
    void shouldVerifyErrorMessageClarity() {
        webTestClient.post()
                .uri("/api/v1/workouts/{workoutId}/exercises/0/sets", completedWorkoutId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "weightKg": 100.0,
                      "reps": 10,
                      "completed": true
                    }
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(containsString("Set operations can only be performed on IN_PROGRESS workout sessions"))
                .jsonPath("$.message")
                .value(containsString("Current status: COMPLETED"));
    }

    // ==================== HELPER METHODS ====================

    private Workout createWorkout(WorkoutStatus status) {
        Workout workout = new Workout();
        workout.setUserId(userId);
        workout.setWorkoutName("Test Workout - " + status);
        workout.setStatus(status);
        workout.setWorkoutType("STRENGTH");
        workout.setWorkoutDate(LocalDateTime.now());
        workout.setExercises(new ArrayList<>());

        // Add a test exercise with a set
        WorkoutExercise exercise = new WorkoutExercise();
        exercise.setExerciseId("test-exercise-123");
        exercise.setExerciseName("Bench Press");
        exercise.setSets(new ArrayList<>());
        workout.getExercises().add(exercise);

        if (status == WorkoutStatus.IN_PROGRESS) {
            workout.setStartedAt(LocalDateTime.now());
        } else if (status == WorkoutStatus.COMPLETED) {
            workout.setStartedAt(LocalDateTime.now().minusHours(1));
            workout.setCompletedAt(LocalDateTime.now());
        }

        return workout;
    }

}