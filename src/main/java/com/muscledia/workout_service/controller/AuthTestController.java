package com.muscledia.workout_service.controller;

import com.muscledia.workout_service.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication Test", description = "Endpoints for testing JWT authentication")
public class AuthTestController {

    private final AuthenticationService authenticationService;

    @GetMapping("/me")
    @Operation(summary = "Get current user info", description = "Get information about the currently authenticated user", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public Mono<Map<String, Object>> getCurrentUser() {
        return Mono.zip(
                authenticationService.getCurrentUserId(),
                authenticationService.getCurrentUsername(),
                authenticationService.getCurrentUserEmail(),
                authenticationService.getCurrentAuthentication()).map(tuple -> {
                    Long userId = tuple.getT1();
                    String username = tuple.getT2();
                    String email = tuple.getT3();
                    var auth = tuple.getT4();

                    return Map.of(
                            "userId", userId,
                            "username", username,
                            "email", email != null ? email : "N/A",
                            "roles", auth.getAuthorities().stream()
                                    .map(authority -> authority.getAuthority())
                                    .toList());
                });
    }

    @GetMapping("/admin-test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin test endpoint", description = "Test endpoint that requires admin role", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Admin access confirmed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public Mono<Map<String, String>> adminTest() {
        return Mono.just(Map.of(
                "message", "Admin access confirmed",
                "timestamp", java.time.Instant.now().toString()));
    }

    @GetMapping("/user-test")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "User test endpoint", description = "Test endpoint that requires user role", security = @SecurityRequirement(name = "bearer-key"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User access confirmed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "User role required")
    })
    public Mono<Map<String, String>> userTest() {
        return Mono.just(Map.of(
                "message", "User access confirmed",
                "timestamp", java.time.Instant.now().toString()));
    }

    @GetMapping("/test")
    public Mono<Map<String, String>> test() {
        return Mono.just(Map.of("message", "JWT Auth working"));
    }
}