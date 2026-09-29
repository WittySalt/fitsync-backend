package com.fitsync.user.controller;

import java.time.Instant;

import com.fitsync.user.dto.OutboxPendingResponse;
import com.fitsync.user.dto.OutboxRelayResponse;
import com.fitsync.user.dto.GoalReconcileResponse;
import com.fitsync.user.messaging.OutboxEventRelay;
import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.entity.OutboxEventEntity;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import com.fitsync.user.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@Validated
@RestController
@RequestMapping("/api/v1/internal")
public class InternalMessagingController {

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxEventRepository outboxEventRepository;
    private final UserService userService;

    public InternalMessagingController(OutboxEventRelay outboxEventRelay,
                                       OutboxEventRepository outboxEventRepository,
                                       UserService userService) {
        this.outboxEventRelay = outboxEventRelay;
        this.outboxEventRepository = outboxEventRepository;
        this.userService = userService;
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

    @PostMapping("/goals/reconcile")
    public ResponseEntity<GoalReconcileResponse> reconcileActiveGoals() {
        return ResponseEntity.ok(new GoalReconcileResponse(userService.republishActiveGoals()));
    }
}
