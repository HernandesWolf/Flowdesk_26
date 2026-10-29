package com.flowdesk.security;

import com.flowdesk.user.Role;
import java.util.UUID;

/** The authenticated principal. Always rebuilt from the database, never trusted from the token alone. */
public record AuthenticatedUser(UUID id, UUID companyId, Role role, String name) {}
