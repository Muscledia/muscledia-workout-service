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
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> (UserPrincipal) auth.getPrincipal())
                .map(UserPrincipal::getUserId)
                .doOnNext(userId -> log.debug("Current user ID: {}", userId))
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user found")));
    }

    public Mono<UserPrincipal> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> (UserPrincipal) auth.getPrincipal())
                .doOnNext(user -> log.debug("Current user: {}", user))
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user found")));
    }

    public Mono<String> getCurrentUsername() {
        return getCurrentUser().map(UserPrincipal::getUsername);
    }

    public Mono<Boolean> hasRole(String role) {
        return getCurrentUser()
                .map(user -> user.hasRole(role))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isAdmin() {
        return hasRole("ADMIN");
    }

    public Mono<Boolean> canAccessResource(Long resourceUserId) {
        return getCurrentUser()
                .map(user -> user.isAdmin() || user.getUserId().equals(resourceUserId))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isCurrentUser(Long userId) {
        return getCurrentUserId()
                .map(currentUserId -> currentUserId.equals(userId))
                .defaultIfEmpty(false);
    }

}