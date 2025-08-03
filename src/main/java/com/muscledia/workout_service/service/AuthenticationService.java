package com.muscledia.workout_service.service;

import com.muscledia.workout_service.dto.UserPrincipal;
import com.muscledia.workout_service.exception.UnauthorizedException;
import com.muscledia.workout_service.security.JwtAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    public Mono<Long> getCurrentUserId() {
        // This is where you would get the NullPointerException. We prevent it below.
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(context -> {
                    Authentication authentication = context.getAuthentication();
                    if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
                        Object principal = jwtAuthToken.getPrincipal();
                        if (principal instanceof UserPrincipal userPrincipal) {
                            log.debug("Current user ID: {}", userPrincipal.getUserId());
                            return userPrincipal.getUserId();
                        } else {
                            log.warn("Authentication principal is not UserPrincipal: {}", principal);
                            return null; // Return null if not expected type
                        }
                    } else {
                        log.warn("Authentication is not JwtAuthenticationToken: {}", authentication);
                        return null; // Return null if not a JWT token
                    }
                })
                .flatMap(Mono::justOrEmpty)
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user or invalid token.")));
    }

    public Mono<UserPrincipal> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> { // Use flatMap here too
                    if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
                        Object principal = jwtAuthToken.getPrincipal();
                        if (principal instanceof UserPrincipal userPrincipal) {
                            log.debug("Current user: {} with role: {}", userPrincipal.getUsername(), userPrincipal.getRole());
                            return Mono.just(userPrincipal);
                        } else {
                            log.warn("Authentication principal is not UserPrincipal: {}", principal);
                            return Mono.empty();
                        }
                    } else {
                        log.warn("Authentication is not JwtAuthenticationToken: {}", authentication.getClass().getName());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user or invalid principal found")));
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