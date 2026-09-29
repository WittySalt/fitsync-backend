package com.fitsync.user.messaging.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus status);

    long countByStatus(OutboxEventStatus status);

    Optional<OutboxEventEntity> findFirstByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus status);
}
