package com.fitsync.backend.service;

import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fitsync.backend.dto.ActiveSessionView;
import com.fitsync.backend.dto.AccountDeletionRequestStatusResponse;
import com.fitsync.backend.dto.CurrentUserResponse;
import com.fitsync.backend.dto.RequestAccountDeletionRequest;
import com.fitsync.backend.dto.SecurityOverviewResponse;
import com.fitsync.backend.dto.UpdateRecoveryEmailRequest;
import com.fitsync.backend.entity.AppUser;
import com.fitsync.backend.entity.AuthProvider;
import com.fitsync.backend.exception.ConflictException;
import com.fitsync.backend.exception.NotFoundException;
import com.fitsync.backend.exception.UnauthorizedException;
import com.fitsync.backend.messaging.DomainEventPublisher;
import com.fitsync.backend.repository.AppUserRepository;
import com.fitsync.backend.security.JwtService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    private final AppUserRepository appUserRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccountDeletionPolicy accountDeletionPolicy;

    public CurrentUserService(AppUserRepository appUserRepository,
                              DomainEventPublisher domainEventPublisher,
                              PasswordEncoder passwordEncoder,
                              JwtService jwtService,
                              AccountDeletionPolicy accountDeletionPolicy) {
        this.appUserRepository = appUserRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accountDeletionPolicy = accountDeletionPolicy;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Authentication authentication) {
        AppUser user = resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public SecurityOverviewResponse getSecurityOverview(Authentication authentication) {
        AppUser user = resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));
        boolean cognitoManaged = user.getAuthProvider() == AuthProvider.COGNITO;
        return new SecurityOverviewResponse(
                user.getAuthProvider().name(),
                cognitoManaged,
                false,
                cognitoManaged,
                user.getEmail(),
                !cognitoManaged,
                1,
                true,
                cognitoManaged ? "COGNITO_PROTECTED" : "LOCAL_ACCOUNT");
    }

    @Transactional(readOnly = true)
    public List<ActiveSessionView> getActiveSessions(Authentication authentication, HttpServletRequest request) {
        // Ensure the caller is provisioned; the session view is derived from the token/request, not the user row.
        resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));

        Instant issuedAt = null;
        Instant expiresAt = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            issuedAt = jwt.getIssuedAt();
            expiresAt = jwt.getExpiresAt();
        } else {
            String token = bearerToken(request);
            if (token != null) {
                issuedAt = jwtService.extractIssuedAt(token);
                expiresAt = jwtService.extractExpiration(token);
            }
        }

        return List.of(new ActiveSessionView(
                "current-session",
                resolveDeviceLabel(request),
                resolveIpAddress(request),
                issuedAt,
                expiresAt,
                true,
                false));
    }

    @Transactional(readOnly = true)
    public AccountDeletionRequestStatusResponse getAccountDeletionStatus(Authentication authentication) {
        AppUser user = resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));
        return toAccountDeletionStatus(user);
    }

    @Transactional
    public SecurityOverviewResponse updateRecoveryEmail(Authentication authentication,
                                                        UpdateRecoveryEmailRequest request) {
        AppUser user = resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new ConflictException("Recovery email is managed by Cognito for this account");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is invalid");
        }

        String normalizedEmail = normalizeEmail(request.email());
        Optional<AppUser> existing = appUserRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new ConflictException("An account with this email already exists");
        }

        user.setEmail(normalizedEmail);
        AppUser saved = appUserRepository.save(user);
        return getSecurityOverviewForUser(saved);
    }

    @Transactional
    public AccountDeletionRequestStatusResponse requestAccountDeletion(Authentication authentication,
                                                                      RequestAccountDeletionRequest request) {
        AppUser user = resolveUser(authentication)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));

        if (!"DELETE".equals(request.confirmationText().trim())) {
            throw new IllegalArgumentException("Confirmation text must be DELETE");
        }

        if (user.getAuthProvider() == AuthProvider.LOCAL) {
            if (!StringUtils.hasText(request.currentPassword())
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException("Current password is invalid");
            }
        }

        if (user.getAccountDeletionRequestedAt() == null) {
            user.setAccountDeletionRequestedAt(Instant.now());
            user = appUserRepository.save(user);
        }

        return toAccountDeletionStatus(user);
    }

    /**
     * Undoes a requested deletion while the grace period is running. The caller holds a Cognito identity token
     * rather than an application token, because an account with a pending deletion is not issued one. A
     * subject that has no account here is never provisioned by this call.
     */
    @Transactional
    public AccountDeletionRequestStatusResponse cancelAccountDeletion(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        String subject = requiredClaim(jwt, "sub");
        requireVerifiedEmail(jwt);
        AppUser user = appUserRepository.findByExternalSubject(subject)
                .orElseThrow(() -> new NotFoundException("Authenticated user is not provisioned in auth-service"));

        if (user.getAccountDeletionRequestedAt() == null) {
            return toAccountDeletionStatus(user);
        }
        if (accountDeletionPolicy.gracePeriodEnded(user, Instant.now())) {
            throw accountDeletionPolicy.deletionFinal(user);
        }

        user.setAccountDeletionRequestedAt(null);
        return toAccountDeletionStatus(appUserRepository.save(user));
    }

    @Transactional
    public AppUser bootstrapCognitoUser(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        String subject = requiredClaim(jwt, "sub");
        requireVerifiedEmail(jwt);
        Optional<AppUser> existingBySubject = appUserRepository.findByExternalSubject(subject);
        if (existingBySubject.isPresent()) {
            AppUser existing = existingBySubject.get();
            accountDeletionPolicy.requireNoPendingDeletion(existing);
            return existing;
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
        user.setPasswordHash(externalPasswordMarker());

        AppUser savedUser = appUserRepository.save(user);
        publishUserRegistered(savedUser);
        return savedUser;
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
        // Pool users who sign up without a name get their subject (a UUID) as username, which is
        // not something a person should be shown as their name.
        if (StringUtils.hasText(username) && !isIdentifierLike(username.trim(), jwt.getSubject())) {
            return username.trim();
        }

        int separator = fallbackEmail.indexOf('@');
        return separator > 0 ? fallbackEmail.substring(0, separator) : fallbackEmail;
    }

    private boolean isIdentifierLike(String username, String subject) {
        return username.equals(subject) || UUID_PATTERN.matcher(username).matches();
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : email;
    }

    private String externalPasswordMarker() {
        return "{EXTERNAL}:" + UUID.randomUUID();
    }

    private CurrentUserResponse toResponse(AppUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getAuthProvider().name());
    }

    private SecurityOverviewResponse getSecurityOverviewForUser(AppUser user) {
        boolean cognitoManaged = user.getAuthProvider() == AuthProvider.COGNITO;
        return new SecurityOverviewResponse(
                user.getAuthProvider().name(),
                cognitoManaged,
                false,
                cognitoManaged,
                user.getEmail(),
                !cognitoManaged,
                1,
                true,
                cognitoManaged ? "COGNITO_PROTECTED" : "LOCAL_ACCOUNT");
    }

    private String bearerToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    private String resolveDeviceLabel(HttpServletRequest request) {
        if (request == null) {
            return "Current session";
        }
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return "Current session";
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("edg")) {
            return "This browser (Edge)";
        }
        if (normalized.contains("chrome")) {
            return "This browser (Chrome)";
        }
        if (normalized.contains("safari") && !normalized.contains("chrome")) {
            return "This browser (Safari)";
        }
        if (normalized.contains("firefox")) {
            return "This browser (Firefox)";
        }
        return "This browser";
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int commaIndex = forwarded.indexOf(',');
            return commaIndex > 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private AccountDeletionRequestStatusResponse toAccountDeletionStatus(AppUser user) {
        return new AccountDeletionRequestStatusResponse(
                user.getAccountDeletionRequestedAt() == null ? "NOT_REQUESTED" : "REQUESTED",
                user.getAccountDeletionRequestedAt(),
                accountDeletionPolicy.scheduledFor(user.getAccountDeletionRequestedAt()),
                user.getAuthProvider() == AuthProvider.LOCAL);
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
