package com.flowdesk.attachment;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/service-orders/{orderId}/attachments")
@Tag(name = "Attachments")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentDtos.Response upload(@PathVariable UUID orderId, @RequestParam("file") MultipartFile file) {
        return service.upload(orderId, file);
    }

    @GetMapping
    public List<AttachmentDtos.Response> list(@PathVariable UUID orderId) {
        return service.list(orderId);
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable UUID orderId, @PathVariable UUID attachmentId) {
        AttachmentService.Download d = service.download(orderId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.sizeBytes())
                // Always a download, never rendered inline: an uploaded file can never run as a page.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.filename(), StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }
}
