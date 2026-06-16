package com.fitsync.backend.controller;

import com.fitsync.backend.dto.AuthResponse;
import com.fitsync.backend.dto.CurrentUserResponse;
import com.fitsync.backend.dto.LoginRequest;
import com.fitsync.backend.dto.RegisterRequest;
import com.fitsync.backend.entity.AppUser;
import com.fitsync.backend.service.AuthService;
import com.fitsync.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/bootstrap")
    public AuthResponse bootstrap(Authentication authentication) {
        AppUser user = currentUserService.bootstrapCognitoUser(authentication);
        return authService.issueToken(user);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return currentUserService.getCurrentUser(authentication);
    }
}
