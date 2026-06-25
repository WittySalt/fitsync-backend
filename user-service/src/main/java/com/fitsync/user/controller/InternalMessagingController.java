package com.fitsync.user.controller;

import com.fitsync.user.dto.OutboxRelayResponse;
import com.fitsync.user.dto.GoalReconcileResponse;
import com.fitsync.user.messaging.OutboxEventRelay;
import com.fitsync.user.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@Validated
@RestController
@RequestMapping("/api/v1/internal")
public class InternalMessagingController {

    private final OutboxEventRelay outboxEventRelay;
    private final UserService userService;

    public InternalMessagingController(OutboxEventRelay outboxEventRelay,
                                       UserService userService) {
        this.outboxEventRelay = outboxEventRelay;
        this.userService = userService;
    }

    @PostMapping("/outbox/publish")
    public ResponseEntity<OutboxRelayResponse> publishPendingBatch(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int batchSize) {
        int publishedCount = outboxEventRelay.publishPendingBatch(batchSize);
        return ResponseEntity.ok(new OutboxRelayResponse(batchSize, publishedCount));
    }

    @PostMapping("/goals/reconcile")
    public ResponseEntity<GoalReconcileResponse> reconcileActiveGoals() {
        return ResponseEntity.ok(new GoalReconcileResponse(userService.republishActiveGoals()));
    }
}
