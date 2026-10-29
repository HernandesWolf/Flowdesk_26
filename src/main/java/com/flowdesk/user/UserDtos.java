package com.flowdesk.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 180) String email,
            // BCrypt only uses the first 72 bytes, so longer passwords would be silently truncated.
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role) {}

    public record ActiveRequest(@NotNull Boolean active) {}

    public record Response(UUID id, UUID companyId, String name, String email, Role role, boolean active, Instant createdAt) {

        public static Response from(User u) {
            return new Response(
                    u.getId(), u.getCompanyId(), u.getName(), u.getEmail(), u.getRole(), u.isActive(), u.getCreatedAt());
        }
    }
}
