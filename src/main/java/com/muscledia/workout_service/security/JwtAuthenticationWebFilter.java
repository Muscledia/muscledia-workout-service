package com.muscledia.workout_service.security;

import com.muscledia.workout_service.dto.UserPrincipal;
import com.muscledia.workout_service.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        if (token.isEmpty()) {
            return chain.filter(exchange);
        }

        return validateAndSetAuthentication(token)
                .flatMap(auth -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .onErrorResume(error -> {
                    log.error("JWT authentication error: {}", error.getMessage());
                    return handleAuthenticationError(exchange);
                });
    }

    private Mono<JwtAuthenticationToken> validateAndSetAuthentication(String token) {
        return Mono.fromCallable(() -> {
            if (!jwtService.validateToken(token)) {
                throw new RuntimeException("Invalid JWT token");
            }

            Long userId = jwtService.extractUserId(token);
            String username = jwtService.extractUsername(token);
            String role = jwtService.extractRole(token);
            Set<String> permissions = jwtService.extractPermissions(token);

            log.debug("Authenticated user: {} with role: {} and permissions: {}", username, role, permissions);

            UserPrincipal principal = new UserPrincipal(userId, username, role, permissions);

            // Create authorities from role and permissions
            Collection<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(permission -> new SimpleGrantedAuthority("PERMISSION_" + permission))
                    .collect(Collectors.toList());

            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            return new JwtAuthenticationToken(principal, token, authorities);
        });
    }

    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        String body = "{\"error\": \"Unauthorized\", \"message\": \"Invalid or missing authentication token\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}