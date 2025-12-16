package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.dto.response.ApiResponse;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.WorkoutCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WorkoutCalendarController {

    private final WorkoutCalendarService calendarService;
    private final AuthenticationService authenticationService;

    /**
     * Get workout calendar data for a date range
     */
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Map<LocalDate, Long>>>> getWorkoutCalendar(
            @RequestParam Instant startDate,
            @RequestParam Instant endDate) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    log.info("Getting workout calendar for user {} from {} to {}",
                            userId, startDate, endDate);
                    return calendarService.getWorkoutCalendar(userId, startDate, endDate);
                })
                .map(calendar -> ResponseEntity.ok(
                        ApiResponse.success("Workout calendar retrieved successfully", calendar)))
                .onErrorResume(e -> {
                    log.error("Error getting workout calendar", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to retrieve workout calendar")));
                });
    }

    /**
     * Get workout dates for a specific month
     */
    @GetMapping("/month")
    public Mono<ResponseEntity<ApiResponse<List<LocalDate>>>> getWorkoutDatesForMonth(
            @RequestParam @Min(2020) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month) {

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    log.info("Getting workout dates for user {} in {}/{}", userId, year, month);
                    return calendarService.getWorkoutDatesForMonth(userId, year, month);
                })
                .map(dates -> ResponseEntity.ok(
                        ApiResponse.success("Workout dates retrieved successfully", dates)))
                .onErrorResume(e -> {
                    log.error("Error getting workout dates", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to retrieve workout dates")));
                });
    }

    /**
     * Get workout count for current month
     */
    @GetMapping("/current-month-count")
    public Mono<ResponseEntity<ApiResponse<Long>>> getCurrentMonthWorkoutCount() {
        YearMonth currentMonth = YearMonth.now();

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    log.info("Getting current month workout count for user {}", userId);
                    return calendarService.getWorkoutDatesForMonth(
                            userId,
                            currentMonth.getYear(),
                            currentMonth.getMonthValue());
                })
                .map(dates -> ResponseEntity.ok(
                        ApiResponse.success("Current month workout count retrieved",
                                (long) dates.size())))
                .onErrorResume(e -> {
                    log.error("Error getting current month count", e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to retrieve workout count")));
                });
    }

    /**
     * Get workout count for a specific date
     */
    @GetMapping("/date")
    public Mono<ResponseEntity<ApiResponse<Long>>> getWorkoutCountForDate(
            @RequestParam String date) {

        LocalDate localDate = LocalDate.parse(date);

        return authenticationService.getCurrentUserId()
                .flatMap(userId -> {
                    log.info("Getting workout count for user {} on {}", userId, localDate);
                    return calendarService.getWorkoutCountForDate(userId, localDate);
                })
                .map(count -> ResponseEntity.ok(
                        ApiResponse.success("Workout count retrieved successfully", count)))
                .onErrorResume(e -> {
                    log.error("Error getting workout count for date {}", localDate, e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(ApiResponse.error("Failed to retrieve workout count")));
                });
    }
}