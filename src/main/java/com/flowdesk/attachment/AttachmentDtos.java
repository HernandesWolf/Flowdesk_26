package com.flowdesk.attachment;

import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {

    private AttachmentDtos() {}

    public record Response(
            UUID id, String filename, String contentType, long sizeBytes, UUID uploadedById, String uploadedByName, Instant createdAt) {

        public static Response from(Attachment a) {
            return new Response(
                    a.getId(), a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(),
                    a.getUploadedBy().getId(), a.getUploadedBy().getName(), a.getCreatedAt());
        }
    }
}
