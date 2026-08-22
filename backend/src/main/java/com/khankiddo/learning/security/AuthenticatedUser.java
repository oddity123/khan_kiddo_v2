package com.khankiddo.learning.security;

import com.khankiddo.learning.model.UserRole;

public record AuthenticatedUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(role);
    }
}
