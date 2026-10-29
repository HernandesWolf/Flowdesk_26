package com.flowdesk.auth;

import com.flowdesk.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterCompanyRequest(
            @NotBlank @Size(max = 150) String companyName,
            @NotBlank @Size(max = 120) String adminName,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserDtos.Response user) {}
}
