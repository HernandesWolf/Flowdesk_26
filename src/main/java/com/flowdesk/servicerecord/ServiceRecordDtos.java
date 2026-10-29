package com.flowdesk.servicerecord;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ServiceRecordDtos {

    private ServiceRecordDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 5000) String content,
            @Min(1) @Max(1440) Integer minutesSpent) {}

    public record Response(UUID id, UUID authorId, String authorName, String content, Integer minutesSpent, Instant createdAt) {

        public static Response from(ServiceRecord r) {
            return new Response(
                    r.getId(), r.getAuthor().getId(), r.getAuthor().getName(), r.getContent(), r.getMinutesSpent(), r.getCreatedAt());
        }
    }
}
