package com.fitsync.backend.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.backend.dto.CurrentUserResponse;
import com.fitsync.backend.entity.AppUser;
import com.fitsync.backend.entity.AuthProvider;
import com.fitsync.backend.exception.ConflictException;
import com.fitsync.backend.exception.NotFoundException;
import com.fitsync.backend.exception.UnauthorizedException;
import com.fitsync.backend.messaging.DomainEventPublisher;
import com.fitsync.backend.repository.AppUserRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    private static final String EXTERNAL_PASSWORD_MARKER = "{EXTERNAL}";

    private final AppUserRepository appUserRepository;
    private final DomainEventPublisher domainEventPublisher;

    public CurrentUserService(AppUserRepository appUserRepository,
                              DomainEventPublisher domainEventPublisher) {
        this.appUserRepository = appUserRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Authentication authentication) {
        AppUser user = resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));
        return toResponse(user);
    }

    @Transactional
    public CurrentUserResponse bootstrapCognitoUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        String subject = requiredClaim(jwt, "sub");
        requireVerifiedEmail(jwt);
        Optional<AppUser> existingBySubject = appUserRepository.findByExternalSubject(subject);
        if (existingBySubject.isPresent()) {
            return toResponse(existingBySubject.get());
        }

        String email = normalizeEmail(requiredClaim(jwt, "email"));
        if (appUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("A local account with this email already exists and requires manual migration");
        }

        AppUser user = new AppUser();
        user.setAuthProvider(AuthProvider.COGNITO);
        user.setExternalSubject(subject);
        user.setEmail(email);
        user.setDisplayName(resolveDisplayName(jwt, email));
        user.setPasswordHash(EXTERNAL_PASSWORD_MARKER);

        AppUser savedUser = appUserRepository.save(user);
        publishUserRegistered(savedUser);
        return toResponse(savedUser);
    }

    private Optional<AppUser> resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (StringUtils.hasText(subject)) {
                return appUserRepository.findByExternalSubject(subject);
            }
            return Optional.empty();
        }

        String username = extractUsername(principal);
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }
        return appUserRepository.findByEmailIgnoreCase(normalizeEmail(username));
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("A Cognito bearer token is required for this operation");
        }
        return jwt;
    }

    private String extractUsername(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String stringPrincipal) {
            return stringPrincipal;
        }
        return null;
    }

    private String requiredClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (!StringUtils.hasText(value)) {
            throw new UnauthorizedException("Required token claim is missing: " + claimName);
        }
        return value;
    }

    private void requireVerifiedEmail(Jwt jwt) {
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new UnauthorizedException("A verified email is required for Cognito provisioning");
        }
    }

    private String resolveDisplayName(Jwt jwt, String fallbackEmail) {
        String name = jwt.getClaimAsString("name");
        if (StringUtils.hasText(name)) {
            return name.trim();
        }

        String username = jwt.getClaimAsString("cognito:username");
        if (StringUtils.hasText(username)) {
            return username.trim();
        }

        int separator = fallbackEmail.indexOf('@');
        return separator > 0 ? fallbackEmail.substring(0, separator) : fallbackEmail;
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : email;
    }

    private CurrentUserResponse toResponse(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getAuthProvider().name());
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
