package com.fitsync.backend.controller;

import com.fitsync.backend.dto.OutboxRelayResponse;
import com.fitsync.backend.messaging.OutboxEventRelay;
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
@RequestMapping("/api/v1/internal/outbox")
public class InternalOutboxController {

    private final OutboxEventRelay outboxEventRelay;

    public InternalOutboxController(OutboxEventRelay outboxEventRelay) {
        this.outboxEventRelay = outboxEventRelay;
    }

    @PostMapping("/publish")
    public ResponseEntity<OutboxRelayResponse> publishPendingBatch(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int batchSize) {
        int publishedCount = outboxEventRelay.publishPendingBatch(batchSize);
        return ResponseEntity.ok(new OutboxRelayResponse(batchSize, publishedCount));
    }
}
