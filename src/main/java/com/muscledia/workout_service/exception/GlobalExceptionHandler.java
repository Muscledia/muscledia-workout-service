package com.muscledia.workout_service.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(ResourceNotFoundException ex) {
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.NOT_FOUND.value(),
                                ex.getMessage(),
                                LocalDateTime.now());
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
        }

        @ExceptionHandler(ValidationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleValidationException(ValidationException ex) {
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                ex.getMessage(),
                                LocalDateTime.now());
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolationException(ConstraintViolationException ex) {
                Map<String, Object> details = new HashMap<>();

                String violations = ex.getConstraintViolations().stream()
                                .map(violation -> {
                                        String propertyPath = violation.getPropertyPath().toString();
                                        String message = violation.getMessage();
                                        details.put(propertyPath, message);
                                        return propertyPath + ": " + message;
                                })
                                .collect(Collectors.joining(", "));

                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Validation failed: " + violations,
                                LocalDateTime.now(),
                                details);
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(ExternalApiException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleExternalApiException(ExternalApiException ex) {
                Map<String, Object> details = new HashMap<>();
                details.put("apiName", ex.getApiName());
                details.put("endpoint", ex.getEndpoint());

                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.SERVICE_UNAVAILABLE.value(),
                                ex.getMessage(),
                                LocalDateTime.now(),
                                details);
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse));
        }

        @ExceptionHandler(WebExchangeBindException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleWebExchangeBindException(WebExchangeBindException ex) {
                Map<String, Object> details = new HashMap<>();
                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));

                // Also handle global errors (like our custom @ValidWorkout annotation)
                ex.getBindingResult().getGlobalErrors()
                                .forEach(error -> details.put("global", error.getDefaultMessage()));

                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Validation failed",
                                LocalDateTime.now(),
                                details);
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(IllegalArgumentException ex) {
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Invalid argument: " + ex.getMessage(),
                                LocalDateTime.now());
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        }

        @ExceptionHandler(Exception.class)
        public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "An unexpected error occurred",
                                LocalDateTime.now());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
        }

        @ExceptionHandler(SomeDuplicateEntryException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleSomeDuplicateEntryException(SomeDuplicateEntryException ex) {
                ErrorResponse errorResponse = new ErrorResponse(
                        HttpStatus.CONFLICT.value(), // Use CONFLICT (409) for duplicate entry
                        ex.getMessage(),
                        LocalDateTime.now());
                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
        }

        // It's good practice to also have the ErrorResponse record/class
        // If you don't have it, define it like this:
        record ErrorResponse(int status, String message, LocalDateTime timestamp, Map<String, Object> details) {
                public ErrorResponse(int status, String message, LocalDateTime timestamp) {
                        this(status, message, timestamp, null);
                }
        }

}