package com.fitsync.backend.controller;

import java.util.List;

import com.fitsync.backend.dto.ActiveSessionView;
import com.fitsync.backend.dto.SecurityOverviewResponse;
import com.fitsync.backend.dto.UpdateRecoveryEmailRequest;
import com.fitsync.backend.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/security")
public class AuthSecurityController {

    private final CurrentUserService currentUserService;

    public AuthSecurityController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/overview")
    public SecurityOverviewResponse overview(Authentication authentication) {
        return currentUserService.getSecurityOverview(authentication);
    }

    @GetMapping("/sessions")
    public List<ActiveSessionView> sessions(Authentication authentication, HttpServletRequest request) {
        return currentUserService.getActiveSessions(authentication, request);
    }

    @PatchMapping("/recovery-email")
    public SecurityOverviewResponse updateRecoveryEmail(Authentication authentication,
                                                        @Valid @RequestBody UpdateRecoveryEmailRequest request) {
        return currentUserService.updateRecoveryEmail(authentication, request);
    }
}
