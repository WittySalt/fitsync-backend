package com.fitsync.nutrition.controller;

import com.fitsync.nutrition.dto.OutboxRelayResponse;
import com.fitsync.nutrition.messaging.OutboxEventRelay;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/internal")
public class InternalMessagingController {

    private final OutboxEventRelay outboxEventRelay;

    public InternalMessagingController(OutboxEventRelay outboxEventRelay) {
        this.outboxEventRelay = outboxEventRelay;
    }

    @PostMapping("/outbox/publish")
    public ResponseEntity<OutboxRelayResponse> publishPendingBatch(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int batchSize) {
        int publishedCount = outboxEventRelay.publishPendingBatch(batchSize);
        return ResponseEntity.ok(new OutboxRelayResponse(batchSize, publishedCount));
    }
}
