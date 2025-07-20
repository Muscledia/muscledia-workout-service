package com.muscledia.workout_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class UserPrincipal {
    private Long userId;
    private String username;
    private String role;
    private Set<String> permissions; // Add permissions support

    // Existing methods
    public boolean hasRole(String role) {
        return this.role.equals(role);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(this.role);
    }

    // New permission-based methods
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean hasAnyPermission(String... permissions) {
        if (this.permissions == null)
            return false;
        for (String permission : permissions) {
            if (this.permissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(String... permissions) {
        if (this.permissions == null)
            return false;
        for (String permission : permissions) {
            if (!this.permissions.contains(permission)) {
                return false;
            }
        }
        return true;
    }
}