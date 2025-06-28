package com.muscledia.workout_service.service;

import com.muscledia.workout_service.security.JwtAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AuthenticationService {

    public Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getUserId)
                .doOnNext(userId -> log.debug("Retrieved current user ID: {}", userId))
                .onErrorResume(throwable -> {
                    log.error("Failed to get current user ID: {}", throwable.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<String> getCurrentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .map(Authentication::getName)
                .doOnNext(username -> log.debug("Retrieved current username: {}", username))
                .onErrorResume(throwable -> {
                    log.error("Failed to get current username: {}", throwable.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<String> getCurrentUserEmail() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getEmail)
                .doOnNext(email -> log.debug("Retrieved current user email: {}", email))
                .onErrorResume(throwable -> {
                    log.error("Failed to get current user email: {}", throwable.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<JwtAuthenticationToken> getCurrentAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .cast(JwtAuthenticationToken.class)
                .doOnNext(auth -> log.debug("Retrieved current authentication for user: {}", auth.getName()))
                .onErrorResume(throwable -> {
                    log.error("Failed to get current authentication: {}", throwable.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Boolean> hasRole(String role) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .map(authentication -> authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.toUpperCase())))
                .defaultIfEmpty(false)
                .doOnNext(hasRole -> log.debug("User has role '{}': {}", role, hasRole))
                .onErrorReturn(false);
    }

    public Mono<Boolean> isCurrentUser(Long userId) {
        return getCurrentUserId()
                .map(currentUserId -> currentUserId.equals(userId))
                .defaultIfEmpty(false)
                .doOnNext(isCurrentUser -> log.debug("Is current user ({}): {}", userId, isCurrentUser));
    }
}