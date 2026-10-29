package com.flowdesk.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {}

    public record Request(
            @NotBlank @Size(max = 150) String name,
            @Email @Size(max = 180) String email,
            @Size(max = 30) String phone,
            @Size(max = 20) String document,
            @Size(max = 300) String address) {}

    public record Response(
            UUID id, String name, String email, String phone, String document, String address, Instant createdAt) {

        public static Response from(Customer c) {
            return new Response(
                    c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getDocument(), c.getAddress(), c.getCreatedAt());
        }
    }
}
