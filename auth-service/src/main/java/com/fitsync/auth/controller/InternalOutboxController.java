package com.fitsync.auth.controller;

import java.time.Instant;

import com.fitsync.auth.dto.OutboxPendingResponse;
import com.fitsync.auth.dto.OutboxRelayResponse;
import com.fitsync.auth.messaging.OutboxEventStatus;
import com.fitsync.auth.messaging.OutboxEventRelay;
import com.fitsync.auth.messaging.entity.OutboxEventEntity;
import com.fitsync.auth.messaging.repository.OutboxEventRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/internal/outbox")
public class InternalOutboxController {

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxEventRepository outboxEventRepository;

    public InternalOutboxController(OutboxEventRelay outboxEventRelay,
                                    OutboxEventRepository outboxEventRepository) {
        this.outboxEventRelay = outboxEventRelay;
        this.outboxEventRepository = outboxEventRepository;
    }

    @PostMapping("/publish")
    public ResponseEntity<OutboxRelayResponse> publishPendingBatch(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int batchSize) {
        int publishedCount = outboxEventRelay.publishPendingBatch(batchSize);
        return ResponseEntity.ok(new OutboxRelayResponse(batchSize, publishedCount));
    }

    /**
     * Reports how much work is backed up in the relay: events still in {@code PENDING}, waiting
     * for {@link #publishPendingBatch} to pick them up. A {@code FAILED} event has already been
     * attempted and recorded its own error, so it is not counted as pending here.
     */
    @GetMapping("/pending")
    public ResponseEntity<OutboxPendingResponse> pendingCount() {
        long pending = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        Instant oldestCreatedAt = outboxEventRepository.findFirstByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
                .map(OutboxEventEntity::getCreatedAt)
                .orElse(null);
        return ResponseEntity.ok(new OutboxPendingResponse(pending, oldestCreatedAt));
    }
}
