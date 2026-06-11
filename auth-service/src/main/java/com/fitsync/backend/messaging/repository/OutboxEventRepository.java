package com.fitsync.backend.messaging.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.backend.messaging.OutboxEventStatus;
import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}
