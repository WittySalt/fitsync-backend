package com.fitsync.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.entity.EmailEntity;

import jakarta.persistence.LockModeType;

public interface EmailRepository extends JpaRepository<EmailEntity, UUID> {

    boolean existsByUserIdAndKindAndDedupeKey(UUID userId, EmailKind kind, String dedupeKey);

    List<EmailEntity> findTop50ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(EmailStatus status, Instant now);

    /**
     * Reads one email and holds its row until the transaction ends. A second dispatcher asking for the
     * same row waits, then reads it as the first one left it, so it sees SENT and does nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailEntity e where e.emailId = :emailId")
    Optional<EmailEntity> findByIdForUpdate(@Param("emailId") UUID emailId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailEntity e where e.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
