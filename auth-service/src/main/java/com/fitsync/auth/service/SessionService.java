package com.fitsync.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.auth.config.SessionProperties;
import com.fitsync.auth.dto.AuthResponse;
import com.fitsync.auth.dto.RefreshResponse;
import com.fitsync.auth.dto.SessionClient;
import com.fitsync.auth.dto.SessionSummary;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.entity.AuthSession;
import com.fitsync.auth.entity.SessionRevocationReason;
import com.fitsync.auth.exception.NotFoundException;
import com.fitsync.auth.exception.SessionException;
import com.fitsync.auth.repository.AppUserRepository;
import com.fitsync.auth.repository.AuthSessionRepository;
import com.fitsync.auth.security.JwtService;
import com.fitsync.auth.security.RefreshTokens;
import com.fitsync.auth.security.RevokedSessionDenyList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side sessions, one per signed-in device. Signing in creates one and returns a short-lived access token
 * carrying its id ({@code sid}) and an opaque refresh token. Each refresh swaps the refresh token for a new one;
 * presenting a token that has already been swapped means two parties hold it, so the whole session is revoked.
 *
 * <p>Revoking only ever sets {@code revoked_at}. It also puts the session on the Redis deny-list that the gateway
 * checks, so its unexpired access tokens stop working at once rather than when they run out.
 */
@Service
public class SessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);

    private final AuthSessionRepository sessionRepository;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final ObjectProvider<RevokedSessionDenyList> denyListProvider;
    private final SessionProperties sessionProperties;

    public SessionService(AuthSessionRepository sessionRepository,
                          AppUserRepository appUserRepository,
                          JwtService jwtService,
                          ObjectProvider<RevokedSessionDenyList> denyListProvider,
                          SessionProperties sessionProperties) {
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
        this.denyListProvider = denyListProvider;
        this.sessionProperties = sessionProperties;
    }

    /** Opens a session for a person who has just proved who they are, and returns the sign-in response. */
    @Transactional
    public AuthResponse startSession(AppUser user, SessionClient client) {
        SessionClient source = client == null ? SessionClient.unknown() : client;
        Instant now = storableNow();
        String refreshToken = RefreshTokens.generate();

        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setDeviceLabel(DeviceLabels.resolve(source.deviceLabelHeader(), source.userAgent()));
        session.setRefreshTokenHash(RefreshTokens.hash(refreshToken));
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(sessionProperties.refreshTokenTtl()));
        sessionRepository.save(session);

        IssuedAccessToken access = issueAccessToken(user, session.getId());
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                "Bearer",
                access.token(),
                refreshToken,
                access.expiresAt(),
                session.getId());
    }

    /**
     * Swaps a refresh token for a new one and a new access token. Refusals that revoke the session are committed
     * even though the call fails, hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = SessionException.class)
    public RefreshResponse refresh(String presentedToken) {
        String presentedHash = RefreshTokens.hash(presentedToken);
        Instant now = storableNow();

        Optional<AuthSession> current = sessionRepository.findByRefreshTokenHash(presentedHash)
                .filter(session -> RefreshTokens.hashesMatch(session.getRefreshTokenHash(), presentedHash));
        if (current.isEmpty()) {
            throw refuseUnknownToken(presentedHash, now);
        }

        AuthSession session = current.get();
        if (session.isRevoked()) {
            throw SessionException.revoked();
        }
        if (!session.getExpiresAt().isAfter(now)) {
            throw SessionException.expired();
        }

        AppUser user = appUserRepository.findById(session.getUserId()).orElse(null);
        if (user == null || user.getAccountDeletionRequestedAt() != null) {
            revoke(List.of(session.getId()), SessionRevocationReason.ACCOUNT_DELETION, now);
            throw SessionException.revoked();
        }

        String nextToken = RefreshTokens.generate();
        int rotated = sessionRepository.rotate(
                session.getId(),
                presentedHash,
                RefreshTokens.hash(nextToken),
                now,
                now.plus(sessionProperties.refreshTokenTtl()));
        if (rotated == 0) {
            // Another refresh with the same token won the race, so this token has just been used twice.
            revoke(List.of(session.getId()), SessionRevocationReason.REFRESH_TOKEN_REUSE, now);
            LOGGER.warn("Refresh token reused concurrently; revoked session {}", forLog(session.getId()));
            throw SessionException.revoked();
        }

        IssuedAccessToken access = issueAccessToken(user, session.getId());
        return new RefreshResponse(access.token(), nextToken, access.expiresAt(), session.getId());
    }

    /** Signs the caller out of the session the request was made with. A token without a session is a no-op. */
    @Transactional
    public void logout(UUID userId, UUID currentSessionId) {
        if (currentSessionId == null) {
            return;
        }
        sessionRepository.findByIdAndUserId(currentSessionId, userId)
                .filter(session -> !session.isRevoked())
                .ifPresent(session -> revoke(List.of(session.getId()), SessionRevocationReason.LOGOUT,
                        storableNow()));
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> listActive(UUID userId, UUID currentSessionId) {
        requireLiveCurrentSession(userId, currentSessionId);
        return sessionRepository.findActiveByUserId(userId, storableNow()).stream()
                .map(session -> new SessionSummary(
                        session.getId(),
                        session.getDeviceLabel(),
                        session.getCreatedAt(),
                        session.getLastSeenAt(),
                        session.getId().equals(currentSessionId)))
                .toList();
    }

    /**
     * Revokes one of the caller's sessions, which may be the current one. A session id that is not the caller's,
     * or not a session id at all, answers 404 so that other people's ids cannot be probed. Revoking a session that
     * is already revoked succeeds and changes nothing.
     */
    @Transactional
    public void revokeOne(UUID userId, UUID currentSessionId, String rawSessionId) {
        requireLiveCurrentSession(userId, currentSessionId);
        UUID sessionId = parseSessionId(rawSessionId);
        AuthSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (!session.isRevoked()) {
            SessionRevocationReason reason = session.getId().equals(currentSessionId)
                    ? SessionRevocationReason.LOGOUT
                    : SessionRevocationReason.REVOKED_BY_USER;
            revoke(List.of(session.getId()), reason, storableNow());
        }
    }

    /** Revokes every session of the caller except the current one, and returns how many were revoked. */
    @Transactional
    public int revokeOthers(UUID userId, UUID currentSessionId) {
        requireLiveCurrentSession(userId, currentSessionId);
        List<UUID> others = sessionRepository.findUnrevokedIds(userId).stream()
                .filter(id -> !id.equals(currentSessionId))
                .toList();
        return revoke(others, SessionRevocationReason.REVOKED_OTHERS, storableNow());
    }

    /** Revokes every session of the person, for example when the account is scheduled for deletion. */
    @Transactional
    public int revokeAllForUser(UUID userId, SessionRevocationReason reason) {
        return revoke(sessionRepository.findUnrevokedIds(userId), reason, storableNow());
    }

    @Transactional(readOnly = true)
    public int countActive(UUID userId) {
        return sessionRepository.findActiveByUserId(userId, storableNow()).size();
    }

    /**
     * The token is unknown as a current token. If it is the one a session last replaced, it has been used twice:
     * revoke that session, since one of the two holders is not its owner.
     */
    private SessionException refuseUnknownToken(String presentedHash, Instant now) {
        Optional<AuthSession> rotatedAway = sessionRepository.findFirstByPreviousRefreshTokenHash(presentedHash)
                .filter(session -> RefreshTokens.hashesMatch(session.getPreviousRefreshTokenHash(), presentedHash));
        if (rotatedAway.isEmpty()) {
            return SessionException.expired();
        }
        AuthSession session = rotatedAway.get();
        if (!session.isRevoked()) {
            revoke(List.of(session.getId()), SessionRevocationReason.REFRESH_TOKEN_REUSE, now);
            LOGGER.warn("Rotated refresh token presented again; revoked session {}", forLog(session.getId()));
        }
        return SessionException.revoked();
    }

    /**
     * Session management is refused to a device whose own session was revoked, even when the gateway could not
     * tell (Redis down): otherwise a signed-out device could still sign the owner out everywhere else.
     */
    private void requireLiveCurrentSession(UUID userId, UUID currentSessionId) {
        if (currentSessionId == null) {
            return;
        }
        boolean live = sessionRepository.findByIdAndUserId(currentSessionId, userId)
                .map(session -> !session.isRevoked())
                .orElse(false);
        if (!live) {
            throw SessionException.revoked();
        }
    }

    private int revoke(List<UUID> sessionIds, SessionRevocationReason reason, Instant now) {
        if (sessionIds.isEmpty()) {
            return 0;
        }
        int revoked = sessionRepository.revokeAll(sessionIds, now, reason);
        denyListProvider.getObject().addAfterCommit(sessionIds);
        return revoked;
    }

    private UUID parseSessionId(String raw) {
        if (raw == null) {
            throw new NotFoundException("Session not found");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Session not found");
        }
    }

    private IssuedAccessToken issueAccessToken(AppUser user, UUID sessionId) {
        // Whole seconds, because that is what the token's exp claim can hold.
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(jwtService.accessTokenLifetime());
        String token = jwtService.generateToken(user.getEmail(), Map.of(
                "userId", user.getId().toString(),
                "displayName", user.getDisplayName(),
                "role", user.getRole().name(),
                JwtService.SESSION_ID_CLAIM, sessionId.toString()), issuedAt, expiresAt);
        return new IssuedAccessToken(token, expiresAt);
    }

    /** A session id for a log line, with line breaks taken out (CWE-117); never a token. */
    private static String forLog(UUID sessionId) {
        return String.valueOf(sessionId).replace("\r", "_").replace("\n", "_");
    }

    /** The current instant at the precision the database keeps (microseconds). */
    private static Instant storableNow() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private record IssuedAccessToken(String token, Instant expiresAt) {
    }
}
