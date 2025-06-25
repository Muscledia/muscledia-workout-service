package com.muscledia.workout_service.security;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Reactive UserDetailsService implementation for JWT-based authentication.
 * Since user details are extracted from JWT tokens, this service is mainly
 * used as a placeholder and should not be called in normal JWT authentication
 * flow.
 */
@Service
public class CustomReactiveUserDetailsService implements ReactiveUserDetailsService {

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        // In JWT authentication, user details are extracted from the token
        // This method should not be called in normal flow
        return Mono.error(new UsernameNotFoundException(
                "UserDetails should be extracted from JWT token, not from database lookup"));
    }
}