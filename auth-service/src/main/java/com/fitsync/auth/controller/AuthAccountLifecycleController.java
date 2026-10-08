package com.fitsync.auth.controller;

import com.fitsync.auth.dto.AccountDeletionRequestStatusResponse;
import com.fitsync.auth.dto.RequestAccountDeletionRequest;
import com.fitsync.auth.service.CurrentUserService;
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

    @PostMapping("/cancel")
    public AccountDeletionRequestStatusResponse cancel(Authentication authentication) {
        return currentUserService.cancelAccountDeletion(authentication);
    }
}
