package com.flowdesk.servicerecord;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-orders/{orderId}/records")
@Tag(name = "Service records")
public class ServiceRecordController {

    private final ServiceRecordService service;

    public ServiceRecordController(ServiceRecordService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRecordDtos.Response add(
            @PathVariable UUID orderId, @Valid @RequestBody ServiceRecordDtos.CreateRequest request) {
        return service.add(orderId, request);
    }

    @GetMapping
    public List<ServiceRecordDtos.Response> list(@PathVariable UUID orderId) {
        return service.list(orderId);
    }
}
