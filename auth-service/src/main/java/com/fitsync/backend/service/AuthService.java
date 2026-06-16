package com.fitsync.backend.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fitsync.backend.messaging.DomainEventPublisher;
import com.fitsync.backend.dto.AuthResponse;
import com.fitsync.backend.dto.LoginRequest;
import com.fitsync.backend.dto.RegisterRequest;
import com.fitsync.backend.entity.AppUser;
import com.fitsync.backend.entity.AuthProvider;
import com.fitsync.backend.exception.ConflictException;
import com.fitsync.backend.exception.UnauthorizedException;
import com.fitsync.backend.repository.AppUserRepository;
import com.fitsync.backend.security.JwtService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DomainEventPublisher domainEventPublisher;

    public AuthService(AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       DomainEventPublisher domainEventPublisher) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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
        return issueToken(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return issueToken(user);
    }

    public AuthResponse issueToken(AppUser user) {
        UserDetails userDetails = User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();
        String token = jwtService.generateToken(userDetails, Map.of(
                "userId", user.getId().toString(),
                "displayName", user.getDisplayName(),
                "role", user.getRole().name()));
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                "Bearer",
                token);
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
