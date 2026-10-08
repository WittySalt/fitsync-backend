package com.fitsync.auth.controller;

import com.fitsync.auth.config.LocalAuthProperties;
import com.fitsync.auth.dto.AuthResponse;
import com.fitsync.auth.dto.CurrentUserResponse;
import com.fitsync.auth.dto.LoginRequest;
import com.fitsync.auth.dto.RegisterRequest;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.exception.NotFoundException;
import com.fitsync.auth.service.AuthService;
import com.fitsync.auth.service.CurrentUserService;
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
    private final LocalAuthProperties localAuthProperties;

    public AuthController(AuthService authService,
                          CurrentUserService currentUserService,
                          LocalAuthProperties localAuthProperties) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.localAuthProperties = localAuthProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        requireLocalAccounts();
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        requireLocalAccounts();
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

    // People sign in through Cognito, so password accounts answer as if the routes did not exist unless a
    // local environment switches them on.
    private void requireLocalAccounts() {
        if (!localAuthProperties.localAccountsEnabled()) {
            throw new NotFoundException("Not found");
        }
    }
}
