package com.example.campus;

import java.util.Set;

public record CurrentUser(Long userId, String username, String realName, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isLeader() {
        return hasRole("ORG_LEADER");
    }
}
