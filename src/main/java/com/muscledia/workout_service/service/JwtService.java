package com.muscledia.workout_service.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.issuer}")
    private String issuer;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    //.requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    // FIXED: Handle userId extraction properly
    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);

        // Try to get userIdLong first (if available)
        Object userIdLong = claims.get("userIdLong");
        if (userIdLong instanceof Long) {
            return (Long) userIdLong;
        }
        if (userIdLong instanceof Integer) {
            return ((Integer) userIdLong).longValue();
        }

        // Fallback to userId as String and convert
        Object userId = claims.get("userId");
        if (userId instanceof String) {
            try {
                return Long.valueOf((String) userId);
            } catch (NumberFormatException e) {
                log.error("Failed to convert userId string to Long: {}", userId);
                return null;
            }
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }

        log.warn("Could not extract userId from token. Available claims: {}", claims.keySet());
        return null;
    }

    // FIXED: Handle roles extraction properly
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = extractClaims(token);
        Object rolesObj = claims.get("roles");

        if (rolesObj instanceof List) {
            return (List<String>) rolesObj;
        }

        // Fallback to single role if available
        String singleRole = claims.get("role", String.class);
        if (singleRole != null) {
            return List.of(singleRole);
        }

        return Collections.emptyList();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        List<String> permissions = extractClaims(token).get("permissions", List.class);
        return new HashSet<>(permissions != null ? permissions : Collections.emptyList());
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }
}