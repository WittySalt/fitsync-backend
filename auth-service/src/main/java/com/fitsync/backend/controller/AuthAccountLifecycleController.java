package com.fitsync.backend.controller;

import com.fitsync.backend.dto.AccountDeletionRequestStatusResponse;
import com.fitsync.backend.dto.RequestAccountDeletionRequest;
import com.fitsync.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/account-deletion")
public class AuthAccountLifecycleController {

    private final CurrentUserService currentUserService;

    public AuthAccountLifecycleController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public AccountDeletionRequestStatusResponse status(Authentication authentication) {
        return currentUserService.getAccountDeletionStatus(authentication);
    }

    @PostMapping("/request")
    public AccountDeletionRequestStatusResponse request(Authentication authentication,
                                                        @Valid @RequestBody RequestAccountDeletionRequest request) {
        return currentUserService.requestAccountDeletion(authentication, request);
    }
}
