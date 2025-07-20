package com.muscledia.workout_service.service;

import com.muscledia.workout_service.dto.UserPrincipal;
import com.muscledia.workout_service.exception.UnauthorizedException;
import com.muscledia.workout_service.security.JwtAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    public Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> ((UserPrincipal) auth.getPrincipal()).getUserId())
                .doOnNext(userId -> log.debug("Current user ID: {}", userId))
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user found")));
    }

    public Mono<UserPrincipal> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> (UserPrincipal) auth.getPrincipal())
                .doOnNext(user -> log.debug("Current user: {} with role: {}", user.getUsername(), user.getRole()))
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user found")));
    }

    public Mono<String> getCurrentUsername() {
        return getCurrentUser()
                .map(UserPrincipal::getUsername);
    }

    public Mono<String> getCurrentUserRole() {
        return getCurrentUser()
                .map(UserPrincipal::getRole);
    }

    public Mono<Boolean> hasRole(String role) {
        return getCurrentUser()
                .map(user -> user.hasRole(role))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> hasPermission(String permission) {
        return getCurrentUser()
                .map(user -> user.hasPermission(permission))
                .defaultIfEmpty(false)
                .doOnNext(hasPermission -> log.debug("User has permission '{}': {}", permission, hasPermission));
    }

    public Mono<Boolean> hasAnyPermission(String... permissions) {
        return getCurrentUser()
                .map(user -> user.hasAnyPermission(permissions))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> hasAllPermissions(String... permissions) {
        return getCurrentUser()
                .map(user -> user.hasAllPermissions(permissions))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isAdmin() {
        return getCurrentUser()
                .map(UserPrincipal::isAdmin)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isCurrentUser(Long userId) {
        return getCurrentUserId()
                .map(currentUserId -> currentUserId.equals(userId))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> canAccessResource(Long resourceUserId) {
        return getCurrentUser()
                .flatMap(user -> {
                    // Admin can access any resource
                    if (user.isAdmin()) {
                        return Mono.just(true);
                    }
                    // User can only access their own resources
                    return Mono.just(user.getUserId().equals(resourceUserId));
                })
                .defaultIfEmpty(false);
    }
}