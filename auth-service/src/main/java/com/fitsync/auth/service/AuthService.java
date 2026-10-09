package com.fitsync.auth.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.fitsync.auth.messaging.DomainEventPublisher;
import com.fitsync.auth.dto.AuthResponse;
import com.fitsync.auth.dto.LoginRequest;
import com.fitsync.auth.dto.RegisterRequest;
import com.fitsync.auth.dto.SessionClient;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.entity.AuthProvider;
import com.fitsync.auth.exception.ConflictException;
import com.fitsync.auth.exception.UnauthorizedException;
import com.fitsync.auth.repository.AppUserRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local password accounts (register and login). Both open a session through {@link SessionService}, exactly as
 * the Cognito bootstrap does, so every sign-in path issues a short-lived access token and a refresh token.
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final DomainEventPublisher domainEventPublisher;
    private final AccountDeletionPolicy accountDeletionPolicy;

    public AuthService(AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       SessionService sessionService,
                       DomainEventPublisher domainEventPublisher,
                       AccountDeletionPolicy accountDeletionPolicy) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.domainEventPublisher = domainEventPublisher;
        this.accountDeletionPolicy = accountDeletionPolicy;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, SessionClient client) {
        if (appUserRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email is already registered");
        }

        AppUser user = new AppUser();
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        AppUser savedUser = appUserRepository.save(user);
        publishUserRegistered(savedUser);
        return sessionService.startSession(savedUser, client);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, SessionClient client) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // Checked after the password so that the state of an account is only revealed to its owner.
        accountDeletionPolicy.requireNoPendingDeletion(user);
        return sessionService.startSession(user, client);
    }

    private void publishUserRegistered(AppUser user) {
        Instant registeredAt = user.getDateTimeCreated() == null ? Instant.now() : user.getDateTimeCreated();
        FitSyncDomainEvent<UserRegisteredPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                registeredAt,
                "auth-service",
                user.getId(),
                new UserRegisteredPayload(
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRole().name(),
                        registeredAt));
        domainEventPublisher.publish("user-events", "AppUser", user.getId().toString(), event);
    }
}
