package com.fitsync.analytics.messaging.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.messaging.OutboxEventStatus;
import com.fitsync.analytics.messaging.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus status);
}
