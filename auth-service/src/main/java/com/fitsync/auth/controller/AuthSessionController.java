package com.fitsync.auth.controller;

import java.util.UUID;

import com.fitsync.auth.dto.RefreshRequest;
import com.fitsync.auth.dto.RefreshResponse;
import com.fitsync.auth.dto.SessionListResponse;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.security.JwtService;
import com.fitsync.auth.service.CurrentUserService;
import com.fitsync.auth.service.SessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refresh, sign-out and the caller's list of signed-in devices. Every endpoint except refresh needs an
 * application token, and every one acts only on the caller's own sessions. The current session is the
 * {@code sid} of the token the request carries.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    public static final String REVOKED_COUNT_HEADER = "X-Revoked-Count";
    private static final String SCOPE_OTHERS = "others";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionService sessionService;
    private final CurrentUserService currentUserService;
    private final JwtService jwtService;

    public AuthSessionController(SessionService sessionService,
                                 CurrentUserService currentUserService,
                                 JwtService jwtService) {
        this.sessionService = sessionService;
        this.currentUserService = currentUserService;
        this.jwtService = jwtService;
    }

    /** Unauthenticated: the refresh token is the credential. */
    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return sessionService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        AppUser user = currentUserService.requireCurrentUser(authentication);
        sessionService.logout(user.getId(), currentSessionId(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public SessionListResponse sessions(Authentication authentication, HttpServletRequest request) {
        AppUser user = currentUserService.requireCurrentUser(authentication);
        return new SessionListResponse(sessionService.listActive(user.getId(), currentSessionId(request)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeOne(Authentication authentication,
                                          HttpServletRequest request,
                                          @PathVariable String sessionId) {
        AppUser user = currentUserService.requireCurrentUser(authentication);
        sessionService.revokeOne(user.getId(), currentSessionId(request), sessionId);
        return ResponseEntity.noContent().build();
    }

    /** Only {@code scope=others} is supported: every session but the current one. */
    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeMany(Authentication authentication,
                                           HttpServletRequest request,
                                           @RequestParam(name = "scope", required = false) String scope) {
        if (!SCOPE_OTHERS.equals(scope)) {
            throw new IllegalArgumentException("scope must be 'others'");
        }
        AppUser user = currentUserService.requireCurrentUser(authentication);
        int revoked = sessionService.revokeOthers(user.getId(), currentSessionId(request));
        return ResponseEntity.noContent()
                .header(REVOKED_COUNT_HEADER, Integer.toString(revoked))
                .build();
    }

    /** The {@code sid} of the bearer token, or {@code null} for a token issued before sessions existed. */
    private UUID currentSessionId(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            return jwtService.extractSessionId(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
