package com.fitsync.nutrition.controller;

import java.time.Instant;

import com.fitsync.nutrition.dto.OutboxPendingResponse;
import com.fitsync.nutrition.dto.OutboxRelayResponse;
import com.fitsync.nutrition.messaging.OutboxEventRelay;
import com.fitsync.nutrition.messaging.OutboxEventStatus;
import com.fitsync.nutrition.messaging.entity.OutboxEventEntity;
import com.fitsync.nutrition.messaging.repository.OutboxEventRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoints to drain the outbox by hand and to see how much of it is still pending. */
@Validated
@RestController
@RequestMapping("/api/v1/internal")
public class InternalMessagingController {

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxEventRepository outboxEventRepository;

    public InternalMessagingController(OutboxEventRelay outboxEventRelay,
                                       OutboxEventRepository outboxEventRepository) {
        this.outboxEventRelay = outboxEventRelay;
        this.outboxEventRepository = outboxEventRepository;
    }

    @PostMapping("/outbox/publish")
    public ResponseEntity<OutboxRelayResponse> publishPendingBatch(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int batchSize) {
        int publishedCount = outboxEventRelay.publishPendingBatch(batchSize);
        return ResponseEntity.ok(new OutboxRelayResponse(batchSize, publishedCount));
    }

    /**
     * Reports how much work is backed up in the relay: events still {@code PENDING}, waiting for
     * {@link #publishPendingBatch} to pick them up. A {@code FAILED} event already recorded its
     * own error on a previous attempt, so it is not counted as pending here.
     */
    @GetMapping("/outbox/pending")
    public ResponseEntity<OutboxPendingResponse> pendingCount() {
        long pending = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        Instant oldestCreatedAt = outboxEventRepository.findFirstByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .map(OutboxEventEntity::getDateTimeCreated)
                .orElse(null);
        return ResponseEntity.ok(new OutboxPendingResponse(pending, oldestCreatedAt));
    }
}
