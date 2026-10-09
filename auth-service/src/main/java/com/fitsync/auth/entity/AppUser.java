package com.fitsync.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(unique = true, length = 120)
    private String externalSubject;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.USER;

    @Column
    private Instant accountDeletionRequestedAt;

    /** Set when the grace period ended and the purge claimed this account; it is never cleared. */
    @Column
    private Instant accountPurgeStartedAt;

    /** The id of the AccountDeleted event the purge wrote to the outbox. */
    @Column
    private UUID accountPurgeEventId;
}
