package com.fitsync.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.entity.EmailEntity;

public interface EmailRepository extends JpaRepository<EmailEntity, UUID> {

    boolean existsByUserIdAndKindAndDedupeKey(UUID userId, EmailKind kind, String dedupeKey);

    List<EmailEntity> findTop50ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(EmailStatus status, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailEntity e where e.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
