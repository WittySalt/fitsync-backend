package com.fitsync.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.auth.entity.AppUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByExternalSubject(String externalSubject);

    /** Accounts whose deletion was requested at or before the cutoff, still pending and not yet claimed. */
    @Query("""
            select u.id from AppUser u
            where u.accountDeletionRequestedAt is not null
              and u.accountDeletionRequestedAt <= :cutoff
              and u.accountPurgeStartedAt is null
            order by u.accountDeletionRequestedAt asc
            """)
    List<UUID> findIdsDueForPurge(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Claims one account for the purge, and only if its request is still pending, older than the cutoff and not
     * claimed yet, so of two instances only one gets 1 back. {@code versioned} bumps the row version, so a
     * cancellation that read the row before the claim fails instead of silently undoing it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update versioned AppUser u
            set u.accountPurgeStartedAt = :now
            where u.id = :id
              and u.accountPurgeStartedAt is null
              and u.accountDeletionRequestedAt is not null
              and u.accountDeletionRequestedAt <= :cutoff
            """)
    int claimForPurge(@Param("id") UUID id, @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    /** Claimed accounts that are not erased yet, the oldest claim first. */
    @Query("""
            select u from AppUser u
            where u.accountPurgeStartedAt is not null
            order by u.accountPurgeStartedAt asc
            """)
    List<AppUser> findPurgesInProgress(Pageable pageable);

    /** Deletes a claimed account row; an unclaimed account is never deleted by this. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AppUser u where u.id = :id and u.accountPurgeStartedAt is not null")
    int purgeClaimed(@Param("id") UUID id);
}
