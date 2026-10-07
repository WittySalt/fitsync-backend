package com.fitsync.user.controller;

import java.time.Instant;

import com.fitsync.user.dto.OutboxPendingResponse;
import com.fitsync.user.dto.OutboxRelayResponse;
import com.fitsync.user.dto.GoalReconcileResponse;
import com.fitsync.user.dto.PersonFactsReconcileResponse;
import com.fitsync.user.messaging.OutboxEventRelay;
import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.entity.OutboxEventEntity;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import com.fitsync.user.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.fitsync.user.security.CurrentUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    private static final Logger LOG = LoggerFactory.getLogger(InternalMessagingController.class);

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

    /**
     * Republishes the profile, the preferences and the latest weight of every user, so that the projections of
     * other services are filled once for users who have not changed anything since those projections existed.
     */
    @PostMapping("/profiles/reconcile")
    public ResponseEntity<PersonFactsReconcileResponse> reconcilePersonFacts() {
        return ResponseEntity.ok(userService.republishPersonFacts());
    }

    /**
     * Republishes every active goal. It acts on all users, so who asked and how many users it touched are logged
     * (the admin by id, never by email).
     */
    @PostMapping("/goals/reconcile")
    public ResponseEntity<GoalReconcileResponse> reconcileActiveGoals(Authentication authentication) {
        GoalReconcileResponse result = userService.republishActiveGoals();
        LOG.info("Goal reconcile by admin {} republished {} active goals of {} users", adminId(authentication),
                result.publishedCount(), result.usersTouched());
        return ResponseEntity.ok(result);
    }

    private static String adminId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUserPrincipal principal) {
            return String.valueOf(principal.userId());
        }
        return authentication == null ? "unknown" : authentication.getName();
    }
}
