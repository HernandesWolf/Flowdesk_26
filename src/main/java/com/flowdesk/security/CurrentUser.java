package com.flowdesk.security;

import com.flowdesk.common.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single entry point to "who is calling and which tenant do they belong to". Services use this to scope
 * every query by company, so tenant isolation never depends on a client-supplied id.
 */
@Component
public class CurrentUser {

    public Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser get() {
        return find().orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    public UUID companyId() {
        return get().companyId();
    }
}
