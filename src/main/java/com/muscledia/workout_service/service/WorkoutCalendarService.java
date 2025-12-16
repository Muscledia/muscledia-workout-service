package com.muscledia.workout_service.service;

import com.muscledia.workout_service.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutCalendarService {

    private final WorkoutRepository workoutRepository;

    /**
     * Get workout dates for calendar view
     * Returns a map of dates to workout counts
     */
    public Mono<Map<LocalDate, Long>> getWorkoutCalendar(
            Long userId,
            Instant startDate,
            Instant endDate) {

        log.info("Getting workout calendar for user {} between {} and {}",
                userId, startDate, endDate);

        // Convert Instant to LocalDateTime
        LocalDateTime startDateTime = LocalDateTime.ofInstant(startDate, ZoneId.systemDefault());
        LocalDateTime endDateTime = LocalDateTime.ofInstant(endDate, ZoneId.systemDefault());

        return workoutRepository
                .findByUserIdAndCompletedAtBetween(userId, startDateTime, endDateTime)
                .filter(workout -> workout.getCompletedAt() != null)
                .collectList()
                .map(workouts -> workouts.stream()
                        .collect(Collectors.groupingBy(
                                workout -> workout.getCompletedAt().toLocalDate(),
                                Collectors.counting()
                        )));
    }

    /**
     * Get workout dates for a specific month
     * Returns list of dates that have completed workouts
     */
    public Mono<List<LocalDate>> getWorkoutDatesForMonth(
            Long userId,
            int year,
            int month) {

        log.info("Getting workout dates for user {} in {}/{}", userId, year, month);

        // Calculate start and end of month
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);

        LocalDateTime startDateTime = startOfMonth.atStartOfDay();
        LocalDateTime endDateTime = endOfMonth.atTime(23, 59, 59);

        return workoutRepository
                .findByUserIdAndCompletedAtBetween(userId, startDateTime, endDateTime)
                .filter(workout -> workout.getCompletedAt() != null)
                .map(workout -> workout.getCompletedAt().toLocalDate())
                .distinct()
                .collectList()
                .map(dates -> dates.stream().sorted().collect(Collectors.toList()));
    }

    /**
     * Get workout count for a specific date
     */
    public Mono<Long> getWorkoutCountForDate(Long userId, LocalDate date) {
        log.debug("Getting workout count for user {} on {}", userId, date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        return workoutRepository
                .findByUserIdAndCompletedAtBetween(userId, startOfDay, endOfDay)
                .count();
    }
}