package com.muscledia.workout_service.security;

import com.muscledia.workout_service.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtService jwtService;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return extractToken(exchange)
                .flatMap(this::authenticateToken)
                .flatMap(authentication -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<String> extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            return Mono.just(token);
        }
        return Mono.empty();
    }

    private Mono<Authentication> authenticateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.empty();
        }

        try {
            if (!jwtService.validateToken(token)) {
                log.debug("Invalid JWT token");
                return Mono.empty();
            }

            String username = jwtService.extractUsername(token);
            Long userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            List<String> roles = jwtService.extractRoles(token);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());

            JwtAuthenticationToken authToken = new JwtAuthenticationToken(
                    username, userId, email, authorities, token);
            authToken.setAuthenticated(true);

            log.debug("Successfully authenticated user: {} with roles: {}", username, roles);
            return Mono.just(authToken);

        } catch (Exception e) {
            log.error("Failed to authenticate token: {}", e.getMessage());
            return Mono.empty();
        }
    }
}