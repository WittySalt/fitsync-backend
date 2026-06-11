package com.fitsync.nutrition.messaging.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.messaging.OutboxEventStatus;
import com.fitsync.nutrition.messaging.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus status);
}
