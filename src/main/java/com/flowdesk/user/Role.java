package com.flowdesk.user;

/** Access roles inside a company (tenant). */
public enum Role {
    ADMIN,
    MANAGER,
    TECHNICIAN;

    public boolean isManagerOrAdmin() {
        return this == ADMIN || this == MANAGER;
    }
}
