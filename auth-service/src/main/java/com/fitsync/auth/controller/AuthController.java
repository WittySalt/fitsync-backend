package com.fitsync.auth.controller;

import com.fitsync.auth.config.LocalAuthProperties;
import com.fitsync.auth.dto.AuthResponse;
import com.fitsync.auth.dto.CurrentUserResponse;
import com.fitsync.auth.dto.LoginRequest;
import com.fitsync.auth.dto.RegisterRequest;
import com.fitsync.auth.dto.SessionClient;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.exception.NotFoundException;
import com.fitsync.auth.service.AuthService;
import com.fitsync.auth.service.CurrentUserService;
import com.fitsync.auth.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
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

    /** Optional name a client gives the device it signs in from; sanitised and cut to 60 characters. */
    public static final String DEVICE_LABEL_HEADER = "X-Device-Label";

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final SessionService sessionService;
    private final LocalAuthProperties localAuthProperties;

    public AuthController(AuthService authService,
                          CurrentUserService currentUserService,
                          SessionService sessionService,
                          LocalAuthProperties localAuthProperties) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.sessionService = sessionService;
        this.localAuthProperties = localAuthProperties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        requireLocalAccounts();
        return authService.register(request, sessionClient(httpRequest));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        requireLocalAccounts();
        return authService.login(request, sessionClient(httpRequest));
    }

    @PostMapping("/bootstrap")
    public AuthResponse bootstrap(Authentication authentication, HttpServletRequest httpRequest) {
        AppUser user = currentUserService.bootstrapCognitoUser(authentication);
        return sessionService.startSession(user, sessionClient(httpRequest));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return currentUserService.getCurrentUser(authentication);
    }

    private static SessionClient sessionClient(HttpServletRequest request) {
        return new SessionClient(request.getHeader(DEVICE_LABEL_HEADER), request.getHeader(HttpHeaders.USER_AGENT));
    }

    // People sign in through Cognito, so password accounts answer as if the routes did not exist unless a
    // local environment switches them on.
    private void requireLocalAccounts() {
        if (!localAuthProperties.localAccountsEnabled()) {
            throw new NotFoundException("Not found");
        }
    }
}
