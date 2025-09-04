package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.dto.response.analytics.ProgressTrackingResponse;
import com.muscledia.workout_service.dto.response.analytics.WorkoutAnalyticsResponse;
import com.muscledia.workout_service.model.analytics.PersonalRecord;
import com.muscledia.workout_service.service.AuthenticationService;
import com.muscledia.workout_service.service.analytics.PersonalRecordMigrationService;
import com.muscledia.workout_service.service.analytics.PersonalRecordService;
import com.muscledia.workout_service.service.analytics.ProgressTrackingService;
import com.muscledia.workout_service.service.analytics.WorkoutAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workout Analytics", description = "Comprehensive workout analytics, progress tracking, and insights (Requires Authentication)")
public class WorkoutAnalyticsController {

        private final WorkoutAnalyticsService analyticsService;
        private final PersonalRecordService personalRecordService;
        private final ProgressTrackingService progressTrackingService;
        private final AuthenticationService authenticationService;
        private final PersonalRecordMigrationService migrationService;

        @GetMapping("/dashboard")
        @Operation(summary = "Get dashboard analytics", description = "Retrieve comprehensive analytics for dashboard view including weekly, monthly, and quarterly insights.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Dashboard analytics retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutAnalyticsResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "500", description = "Error generating analytics")
        })
        public Flux<WorkoutAnalyticsResponse> getDashboardAnalytics() {
                return authenticationService.getCurrentUserId()
                                .flatMapMany(analyticsService::getDashboardAnalytics)
                                .doOnComplete(() -> log.info("Retrieved dashboard analytics"));
        }

        @GetMapping("/period/{period}")
        @Operation(summary = "Get analytics for specific period", description = "Retrieve detailed analytics for a specific time period (WEEKLY, MONTHLY, QUARTERLY, YEARLY, ALL_TIME).", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Period analytics retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkoutAnalyticsResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid period specified"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "500", description = "Error generating analytics")
        })
        public Mono<ResponseEntity<WorkoutAnalyticsResponse>> getPeriodAnalytics(
                        @Parameter(description = "Analysis period", example = "MONTHLY") @PathVariable String period) {

                if (!isValidPeriod(period)) {
                        return Mono.just(ResponseEntity.badRequest().build());
                }

                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> analyticsService.generateAnalytics(userId, period.toUpperCase()))
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.info("Retrieved {} analytics", period));
        }

        @GetMapping("/history/{period}")
        @Operation(summary = "Get historical analytics", description = "Retrieve historical analytics for trend analysis over multiple periods.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Historical analytics retrieved successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid period or periods parameter"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<WorkoutAnalyticsResponse> getHistoricalAnalytics(
                        @Parameter(description = "Analysis period", example = "MONTHLY") @PathVariable String period,
                        @Parameter(description = "Number of periods to look back", example = "6") @RequestParam(defaultValue = "6") int periods) {

                if (!isValidPeriod(period) || periods < 1 || periods > 24) {
                        return Flux.error(new IllegalArgumentException("Invalid period or periods parameter"));
                }

                return authenticationService.getCurrentUserId()
                                .flatMapMany(userId -> analyticsService.getHistoricalAnalytics(userId,
                                                period.toUpperCase(), periods))
                                .doOnComplete(() -> log.info("Retrieved {} periods of {} historical analytics", periods,
                                                period));
        }


        @PostMapping("/fix-exercise-names")
        @Operation(summary = "Fix exercise names in personal records",
                description = "Updates your personal records with proper exercise names instead of 'Exercise [ID]'",
                security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Migration completed successfully"),
                @ApiResponse(responseCode = "401", description = "Authentication required"),
                @ApiResponse(responseCode = "500", description = "Migration failed")
        })
        public Mono<ResponseEntity<String>> fixExerciseNames() {
                log.info("Starting exercise name migration for personal records");

                return authenticationService.getCurrentUserId()
                        .flatMap(migrationService::fixExerciseNamesForUser)
                        .map(count -> {
                                String message = String.format("Migration completed successfully. Updated %d of your personal records with proper exercise names.", count);
                                log.info(message);
                                return ResponseEntity.ok(message);
                        })
                        .onErrorResume(error -> {
                                String errorMessage = "Migration failed: " + error.getMessage();
                                log.error(errorMessage, error);
                                return Mono.just(ResponseEntity.status(500).body(errorMessage));
                        });
        }

        @GetMapping("/personal-records")
        @Operation(summary = "Get all personal records", description = "Retrieve all personal records for the authenticated user.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Personal records retrieved successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Flux<PersonalRecord> getAllPersonalRecords() {
                return authenticationService.getCurrentUserId()
                                .flatMapMany(personalRecordService::getAllPRs)
                                .doOnComplete(() -> log.info("Retrieved all personal records"));
        }

        @GetMapping("/personal-records/recent")
        @Operation(summary = "Get recent personal records", description = "Retrieve personal records achieved within the specified number of days.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Recent personal records retrieved successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid days parameter"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<ResponseEntity<java.util.List<PersonalRecord>>> getRecentPersonalRecords(
                        @Parameter(description = "Number of days to look back", example = "30") @RequestParam(defaultValue = "30") int days) {

                if (days < 1 || days > 365) {
                        return Mono.just(ResponseEntity.badRequest().build());
                }

                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> personalRecordService.getRecentPRs(userId, days))
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.info("Retrieved recent PRs for {} days", days));
        }

        @GetMapping("/personal-records/exercise/{exerciseId}")
        @Operation(summary = "Get personal records for specific exercise", description = "Retrieve all personal records for a specific exercise.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Exercise personal records retrieved successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "404", description = "Exercise not found")
        })
        public Flux<PersonalRecord> getExercisePersonalRecords(
                        @Parameter(description = "Exercise ID", example = "507f1f77bcf86cd799439011") @PathVariable String exerciseId) {

                return authenticationService.getCurrentUserId()
                                .flatMapMany(userId -> personalRecordService.getExercisePRs(userId, exerciseId))
                                .doOnComplete(() -> log.info("Retrieved PRs for exercise {}", exerciseId));
        }

        @GetMapping("/personal-records/statistics")
        @Operation(summary = "Get personal record statistics", description = "Retrieve statistics about personal records including total count and recent achievements.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "PR statistics retrieved successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<PersonalRecordService.PRStatistics> getPRStatistics() {
                return authenticationService.getCurrentUserId()
                                .flatMap(personalRecordService::getPRStatistics)
                                .doOnSuccess(stats -> log.info("Retrieved PR statistics"));
        }

        @GetMapping("/progress/{exerciseId}")
        @Operation(summary = "Get progress tracking for exercise", description = "Retrieve detailed progress tracking data for a specific exercise including trends and predictions.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Progress tracking data retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgressTrackingResponse.class))),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "404", description = "Exercise not found or no progress data available")
        })
        public Mono<ResponseEntity<ProgressTrackingResponse>> getExerciseProgress(
                        @Parameter(description = "Exercise ID", example = "507f1f77bcf86cd799439011") @PathVariable String exerciseId,
                        @Parameter(description = "Tracking period in days", example = "90") @RequestParam(defaultValue = "90") int days) {

                if (days < 7 || days > 365) {
                        return Mono.just(ResponseEntity.badRequest().build());
                }

                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> progressTrackingService.getExerciseProgress(userId, exerciseId,
                                                days))
                                .map(ResponseEntity::ok)
                                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                                .doOnSuccess(response -> log.info("Retrieved progress for exercise {} over {} days",
                                                exerciseId, days));
        }

        @PostMapping("/check-pr")
        @Operation(summary = "Check if lift would be a PR", description = "Check if a potential lift would be a personal record without recording it.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "PR check completed successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                        @ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public Mono<ResponseEntity<Boolean>> checkPotentialPR(
                        @Parameter(description = "Exercise ID") @RequestParam String exerciseId,
                        @Parameter(description = "Record type (MAX_WEIGHT, MAX_VOLUME, etc.)") @RequestParam String recordType,
                        @Parameter(description = "Potential record value") @RequestParam BigDecimal value) {

                if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.just(ResponseEntity.badRequest().build());
                }

                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> personalRecordService.wouldBePR(userId, exerciseId,
                                                recordType.toUpperCase(), value))
                                .map(ResponseEntity::ok)
                                .doOnSuccess(response -> log.info(
                                                "Checked potential PR for exercise {} type {} value {}",
                                                exerciseId, recordType, value));
        }

        @PostMapping("/refresh")
        @Operation(summary = "Refresh analytics", description = "Force refresh of all analytics data for the authenticated user.", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Analytics refresh initiated successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "500", description = "Error refreshing analytics")
        })
        public Mono<ResponseEntity<String>> refreshAnalytics() {
                return authenticationService.getCurrentUserId()
                                .flatMap(analyticsService::refreshAnalytics)
                                .thenReturn(ResponseEntity.ok("Analytics refresh initiated successfully"))
                                .doOnSuccess(response -> log.info("Analytics refresh initiated"));
        }

        @DeleteMapping("/personal-records/{prId}")
        @Operation(summary = "Delete personal record", description = "Delete a personal record (use with caution - for correcting errors only).", security = @SecurityRequirement(name = "bearer-key"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Personal record deleted successfully"),
                        @ApiResponse(responseCode = "401", description = "Authentication required"),
                        @ApiResponse(responseCode = "403", description = "Not authorized to delete this record"),
                        @ApiResponse(responseCode = "404", description = "Personal record not found")
        })
        public Mono<ResponseEntity<String>> deletePersonalRecord(
                        @Parameter(description = "Personal record ID", example = "507f1f77bcf86cd799439011") @PathVariable String prId) {

                return authenticationService.getCurrentUserId()
                                .flatMap(userId -> personalRecordService.deletePR(prId, userId))
                                .thenReturn(ResponseEntity.ok("Personal record deleted successfully"))
                                .onErrorReturn(ResponseEntity.notFound().build())
                                .doOnSuccess(response -> log.info("Deleted personal record {}", prId));
        }

        // Helper methods

        private boolean isValidPeriod(String period) {
                return period != null && java.util.Arrays.asList("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "ALL_TIME")
                                .contains(period.toUpperCase());
        }
}