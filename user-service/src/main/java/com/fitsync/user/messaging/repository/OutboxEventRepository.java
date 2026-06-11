package com.fitsync.user.messaging.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus status);
}
