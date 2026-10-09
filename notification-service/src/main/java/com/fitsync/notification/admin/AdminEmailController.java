package com.fitsync.notification.admin;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitsync.notification.admin.AdminEmailDtos.AvailabilityResponse;
import com.fitsync.notification.admin.AdminEmailDtos.SendRequest;
import com.fitsync.notification.admin.AdminEmailDtos.SendResponse;
import com.fitsync.notification.security.CurrentUserPrincipal;

import jakarta.validation.Valid;

/**
 * The admin console's "send now". The gateway forwards {@code /api/v1/admin/notifications/**} here,
 * and {@code SecurityConfig} requires the ADMIN role for the whole {@code /api/v1/internal/**} tree.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
public class AdminEmailController {

    private final AdminEmailService service;

    public AdminEmailController(AdminEmailService service) {
        this.service = service;
    }

    @GetMapping("/{userId}/emails")
    public AvailabilityResponse availability(@PathVariable UUID userId) {
        return service.availability(userId);
    }

    @PostMapping("/{userId}/emails")
    public SendResponse send(@PathVariable UUID userId, @Valid @RequestBody SendRequest request,
                             @AuthenticationPrincipal CurrentUserPrincipal admin) {
        return service.send(admin.userId(), userId, request.kind());
    }
}
