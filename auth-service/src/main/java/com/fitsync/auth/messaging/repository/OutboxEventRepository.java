package com.fitsync.auth.messaging.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.auth.messaging.OutboxEventStatus;
import com.fitsync.auth.messaging.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    long countByStatus(OutboxEventStatus status);

    Optional<OutboxEventEntity> findFirstByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    /**
     * Events the relay should send now, oldest first: every PENDING event, and FAILED events that still have
     * attempts left and whose retry time has come.
     */
    @Query("""
            select e from OutboxEventEntity e
            where e.status = :pending
               or (e.status = :failed and e.attemptCount < :maxAttempts
                   and (e.nextAttemptAt is null or e.nextAttemptAt <= :now))
            order by e.createdAt asc""")
    List<OutboxEventEntity> findDueForPublishing(@Param("pending") OutboxEventStatus pending,
                                                 @Param("failed") OutboxEventStatus failed,
                                                 @Param("maxAttempts") int maxAttempts,
                                                 @Param("now") Instant now,
                                                 Pageable pageable);

    /**
     * Removes every outbox row of an erased account. Published events keep their payload, and some of those
     * (UserRegistered) carry the email and the name, so they go with the account.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OutboxEventEntity e where e.userId = :userId")
    int purgeAllByUserId(@Param("userId") UUID userId);
}
