package com.fitsync.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One signed-in device. The id is the {@code sid} claim of every access token issued for it. Only hashes of
 * refresh tokens are kept. A session is revoked by setting {@code revokedAt}; the row is never deleted.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String deviceLabel;

    @Column(nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(length = 64)
    private String previousRefreshTokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private SessionRevocationReason revokedReason;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
