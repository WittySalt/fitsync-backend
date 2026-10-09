package com.fitsync.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.auth.entity.AuthSession;
import com.fitsync.auth.entity.SessionRevocationReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AuthSession> findFirstByPreviousRefreshTokenHash(String previousRefreshTokenHash);

    Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);

    /** The person's live sessions, the most recently used first. */
    @Query("""
            select s from AuthSession s
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastSeenAt desc, s.createdAt desc
            """)
    List<AuthSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Swaps the refresh token in one statement, and only if the presented token is still the current one, so two
     * concurrent refreshes with the same token cannot both succeed. Returns the number of rows changed (0 or 1).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
            set s.previousRefreshTokenHash = s.refreshTokenHash,
                s.refreshTokenHash = :newHash,
                s.lastSeenAt = :now,
                s.expiresAt = :expiresAt
            where s.id = :id and s.refreshTokenHash = :currentHash and s.revokedAt is null
            """)
    int rotate(@Param("id") UUID id,
               @Param("currentHash") String currentHash,
               @Param("newHash") String newHash,
               @Param("now") Instant now,
               @Param("expiresAt") Instant expiresAt);

    /**
     * Ids of the person's unrevoked sessions. Any one to keep is filtered out by the caller: a nullable parameter
     * in the query would be untyped on PostgreSQL (42P18).
     */
    @Query("select s.id from AuthSession s where s.userId = :userId and s.revokedAt is null")
    List<UUID> findUnrevokedIds(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
            set s.revokedAt = :now, s.revokedReason = :reason
            where s.id in :ids and s.revokedAt is null
            """)
    int revokeAll(@Param("ids") List<UUID> ids,
                  @Param("now") Instant now,
                  @Param("reason") SessionRevocationReason reason);
}
