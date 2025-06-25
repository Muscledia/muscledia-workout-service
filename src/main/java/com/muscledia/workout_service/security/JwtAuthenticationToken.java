package com.muscledia.workout_service.security;

import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class JwtAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final Long userId;
    private final String email;
    private final String token;

    public JwtAuthenticationToken(String username, Long userId, String email,
            Collection<? extends GrantedAuthority> authorities, String token) {
        super(username, null, authorities);
        this.userId = userId;
        this.email = email;
        this.token = token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return super.getPrincipal(); // username
    }
}