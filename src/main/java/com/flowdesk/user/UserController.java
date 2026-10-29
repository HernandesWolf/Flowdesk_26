package com.flowdesk.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.Response create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDtos.Response> list() {
        return service.list();
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<UserDtos.Response> technicians() {
        return service.listTechnicians();
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.Response setActive(@PathVariable UUID id, @Valid @RequestBody UserDtos.ActiveRequest request) {
        return service.setActive(id, request.active());
    }
}
