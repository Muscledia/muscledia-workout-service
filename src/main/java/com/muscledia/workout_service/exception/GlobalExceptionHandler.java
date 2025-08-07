package com.muscledia.workout_service.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // WORKOUT-SPECIFIC EXCEPTIONS

        @ExceptionHandler(WorkoutNotFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleWorkoutNotFoundException(
                WorkoutNotFoundException ex, ServerWebExchange exchange) {

                log.warn("Workout not found: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                details.put("path", exchange.getRequest().getPath().value());
                details.put("method", exchange.getRequest().getMethod().name());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
        }

        @ExceptionHandler(InvalidWorkoutStateException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleInvalidWorkoutStateException(
                InvalidWorkoutStateException ex) {

                log.warn("Invalid workout state: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getCurrentState() != null) {
                        details.put("currentState", ex.getCurrentState());
                }
                if (ex.getRequiredState() != null) {
                        details.put("requiredState", ex.getRequiredState());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(ExerciseNotFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleExerciseNotFoundException(
                ExerciseNotFoundException ex) {

                log.warn("Exercise not found: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getExerciseId() != null) {
                        details.put("exerciseId", ex.getExerciseId());
                }
                if (ex.getExerciseIndex() != null) {
                        details.put("exerciseIndex", ex.getExerciseIndex());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
        }

        @ExceptionHandler(InvalidSetDataException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleInvalidSetDataException(
                InvalidSetDataException ex) {

                log.warn("Invalid set data: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getFieldName() != null) {
                        details.put("fieldName", ex.getFieldName());
                }
                if (ex.getFieldValue() != null) {
                        details.put("fieldValue", ex.getFieldValue());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        // GENERIC SERVICE EXCEPTIONS

        @ExceptionHandler(ResourceNotFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(
                ResourceNotFoundException ex) {

                log.warn("Resource not found: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getResourceType() != null) {
                        details.put("resourceType", ex.getResourceType());
                }
                if (ex.getResourceId() != null) {
                        details.put("resourceId", ex.getResourceId());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
        }

        @ExceptionHandler(ValidationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleValidationException(ValidationException ex) {

                log.warn("Validation error: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getField() != null) {
                        details.put("field", ex.getField());
                }
                if (ex.getRejectedValue() != null) {
                        details.put("rejectedValue", ex.getRejectedValue());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(SomeDuplicateEntryException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleSomeDuplicateEntryException(
                SomeDuplicateEntryException ex) {

                log.warn("Duplicate entry: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getEntityType() != null) {
                        details.put("entityType", ex.getEntityType());
                }
                if (ex.getDuplicateField() != null) {
                        details.put("duplicateField", ex.getDuplicateField());
                }
                if (ex.getDuplicateValue() != null) {
                        details.put("duplicateValue", ex.getDuplicateValue());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
        }

        @ExceptionHandler(ExternalApiException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleExternalApiException(ExternalApiException ex) {

                log.error("External API error: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getApiName() != null) {
                        details.put("apiName", ex.getApiName());
                }
                if (ex.getEndpoint() != null) {
                        details.put("endpoint", ex.getEndpoint());
                }
                if (ex.getStatusCode() != null) {
                        details.put("statusCode", ex.getStatusCode());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
        }

        // SECURITY EXCEPTIONS

        @ExceptionHandler(InsufficientPermissionException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleInsufficientPermissionException(
                InsufficientPermissionException ex) {

                log.warn("Insufficient permissions: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getRequiredPermission() != null) {
                        details.put("requiredPermission", ex.getRequiredPermission());
                }
                if (ex.getResource() != null) {
                        details.put("resource", ex.getResource());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
        }

        @ExceptionHandler(AuthenticationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleAuthenticationException(
                AuthenticationException ex) {

                log.warn("Authentication failed: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.UNAUTHORIZED.value(),
                        "Authentication required",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleAccessDeniedException(AccessDeniedException ex) {

                log.warn("Access denied: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        "Access denied",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
        }

        // BUSINESS LOGIC EXCEPTIONS

        @ExceptionHandler(BusinessRuleViolationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleBusinessRuleViolationException(
                BusinessRuleViolationException ex) {

                log.warn("Business rule violation: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getRuleName() != null) {
                        details.put("ruleName", ex.getRuleName());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(DataIntegrityException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleDataIntegrityException(
                DataIntegrityException ex) {

                log.warn("Data integrity violation: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getConstraintName() != null) {
                        details.put("constraintName", ex.getConstraintName());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(ConcurrentModificationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleConcurrentModificationException(
                ConcurrentModificationException ex) {

                log.warn("Concurrent modification detected: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                details.put("entityId", ex.getEntityId());
                details.put("expectedVersion", ex.getExpectedVersion());
                details.put("actualVersion", ex.getActualVersion());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "Resource was modified by another user. Please refresh and try again.",
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
        }

        @ExceptionHandler(RateLimitExceededException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleRateLimitExceededException(
                RateLimitExceededException ex) {

                log.warn("Rate limit exceeded: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                if (ex.getRetryAfterSeconds() != null) {
                        details.put("retryAfterSeconds", ex.getRetryAfterSeconds());
                }

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        ex.getMessage(),
                        LocalDateTime.now(),
                        details);

                ResponseEntity<ErrorResponse> response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(errorResponse);

                if (ex.getRetryAfterSeconds() != null) {
                        response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .header("Retry-After", ex.getRetryAfterSeconds().toString())
                                .body(errorResponse);
                }

                return Mono.just(response);
        }

        // SPRING FRAMEWORK EXCEPTIONS

        @ExceptionHandler(ConstraintViolationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolationException(
                ConstraintViolationException ex) {

                log.warn("Constraint violation: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();
                ex.getConstraintViolations().forEach(violation -> {
                        String propertyPath = violation.getPropertyPath().toString();
                        String message = violation.getMessage();
                        details.put(propertyPath, message);
                });

                String violations = ex.getConstraintViolations().stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .collect(Collectors.joining(", "));

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed: " + violations,
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(WebExchangeBindException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleWebExchangeBindException(WebExchangeBindException ex) {

                log.warn("Binding exception: {}", ex.getMessage());

                Map<String, Object> details = new HashMap<>();

                // Handle field errors
                ex.getBindingResult().getFieldErrors()
                        .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));

                // Handle global errors (like custom validation annotations)
                ex.getBindingResult().getGlobalErrors()
                        .forEach(error -> details.put("global", error.getDefaultMessage()));

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Validation failed",
                        LocalDateTime.now(),
                        details);

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(DuplicateKeyException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleDuplicateKeyException(DuplicateKeyException ex) {

                log.warn("Duplicate key violation: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        "Duplicate entry detected",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleDataIntegrityViolationException(
                DataIntegrityViolationException ex) {

                log.warn("Data integrity violation: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Data integrity constraint violated",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(ResponseStatusException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleResponseStatusException(ResponseStatusException ex) {

                log.warn("Response status exception: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        ex.getStatusCode().value(),
                        ex.getReason() != null ? ex.getReason() : "An error occurred",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(errorResponse));
        }

        // GENERIC EXCEPTIONS

        @ExceptionHandler(IllegalArgumentException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(IllegalArgumentException ex) {

                log.warn("Illegal argument: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Invalid argument: " + ex.getMessage(),
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(IllegalStateException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleIllegalStateException(IllegalStateException ex) {

                log.warn("Illegal state: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "Invalid operation: " + ex.getMessage(),
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(UnsupportedOperationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleUnsupportedOperationException(
                UnsupportedOperationException ex) {

                log.warn("Unsupported operation: {}", ex.getMessage());

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.NOT_IMPLEMENTED.value(),
                        "Operation not supported: " + ex.getMessage(),
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(errorResponse));
        }

        @ExceptionHandler(RuntimeException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleRuntimeException(RuntimeException ex) {

                log.error("Unexpected runtime exception", ex);

                // Don't expose internal error details in production
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
        }

        @ExceptionHandler(Exception.class)
        public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {

                log.error("Unexpected exception", ex);

                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred",
                        LocalDateTime.now());

                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
        }

        // UTILITY METHODS

        /**
         * Extract meaningful error message from exception chain
         */
        private String extractMeaningfulMessage(Throwable throwable) {
                if (throwable == null) return "Unknown error";

                String message = throwable.getMessage();
                if (message != null && !message.trim().isEmpty()) {
                        return message;
                }

                // Try to get message from cause
                if (throwable.getCause() != null) {
                        return extractMeaningfulMessage(throwable.getCause());
                }

                return throwable.getClass().getSimpleName();
        }

        /**
         * Check if we're running in development mode
         */
        private boolean isDevelopmentMode() {
                // You can inject Environment and check active profiles
                // For now, return false to avoid exposing sensitive info
                return false;
        }

        /**
         * Enhanced error response record with additional utility methods
         */
        public record ErrorResponse(
                int status,
                String message,
                LocalDateTime timestamp,
                Map<String, Object> details,
                String path,
                String method) {

                // Primary constructor
                public ErrorResponse(int status, String message, LocalDateTime timestamp, Map<String, Object> details) {
                        this(status, message, timestamp, details, null, null);
                }

                // Constructor without details
                public ErrorResponse(int status, String message, LocalDateTime timestamp) {
                        this(status, message, timestamp, null, null, null);
                }

                // Constructor with path and method
                public ErrorResponse(int status, String message, LocalDateTime timestamp, String path, String method) {
                        this(status, message, timestamp, null, path, method);
                }

                /**
                 * Create error response with request context
                 */
                public static ErrorResponse withContext(int status, String message, ServerWebExchange exchange) {
                        return new ErrorResponse(
                                status,
                                message,
                                LocalDateTime.now(),
                                null,
                                exchange.getRequest().getPath().value(),
                                exchange.getRequest().getMethod().name()
                        );
                }

                /**
                 * Create error response with details
                 */
                public static ErrorResponse withDetails(int status, String message, Map<String, Object> details) {
                        return new ErrorResponse(status, message, LocalDateTime.now(), details);
                }

                /**
                 * Add detail to error response
                 */
                public ErrorResponse addDetail(String key, Object value) {
                        Map<String, Object> newDetails = details != null ? new HashMap<>(details) : new HashMap<>();
                        newDetails.put(key, value);
                        return new ErrorResponse(status, message, timestamp, newDetails, path, method);
                }

                /**
                 * Check if error response has details
                 */
                public boolean hasDetails() {
                        return details != null && !details.isEmpty();
                }

                /**
                 * Get formatted error message for logging
                 */
                public String getLogMessage() {
                        StringBuilder sb = new StringBuilder();
                        sb.append("HTTP ").append(status).append(": ").append(message);

                        if (path != null) {
                                sb.append(" [").append(method).append(" ").append(path).append("]");
                        }

                        if (hasDetails()) {
                                sb.append(" Details: ").append(details);
                        }

                        return sb.toString();
                }
        }

}