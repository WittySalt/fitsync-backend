package com.fitsync.workout.controller;

import java.time.Instant;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.ExerciseCatalogSummaryResponse;
import com.fitsync.workout.dto.OutboxPendingResponse;
import com.fitsync.workout.dto.OutboxRelayResponse;
import com.fitsync.workout.messaging.OutboxEventRelay;
import com.fitsync.workout.messaging.OutboxEventStatus;
import com.fitsync.workout.messaging.entity.OutboxEventEntity;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.service.ExerciseCatalogImportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator endpoints under {@code /api/v1/internal}, open to the ADMIN role only (see {@code SecurityConfig}):
 * draining and inspecting the outbox by hand, and seeding or summarising the shared exercise catalogue.
 */
@Validated
@RestController
@RequestMapping("/api/v1/internal")
public class InternalMessagingController {

    private final OutboxEventRelay outboxEventRelay;
    private final OutboxEventRepository outboxEventRepository;
    private final ExerciseCatalogImportService exerciseCatalogImportService;
    private final ExerciseRepository exerciseRepository;

    public InternalMessagingController(OutboxEventRelay outboxEventRelay,
                                       OutboxEventRepository outboxEventRepository,
                                       ExerciseCatalogImportService exerciseCatalogImportService,
                                       ExerciseRepository exerciseRepository) {
        this.outboxEventRelay = outboxEventRelay;
        this.outboxEventRepository = outboxEventRepository;
        this.exerciseCatalogImportService = exerciseCatalogImportService;
        this.exerciseRepository = exerciseRepository;
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

    @Operation(summary = "Import bundled free-exercise-db catalog exercises")
    @PostMapping("/workouts/exercises/import-catalog")
    public ExerciseCatalogImportResponse importExerciseCatalog() {
        return exerciseCatalogImportService.importFreeExerciseDbCatalog();
    }

    /**
     * Aggregates catalogue exercises only, never a user's own custom ones, by their type.
     */
    @Operation(summary = "Summarise the shared exercise catalogue by type")
    @GetMapping("/workouts/exercises/summary")
    public ExerciseCatalogSummaryResponse exerciseCatalogSummary() {
        return exerciseRepository.summarizeCatalogByType();
    }
}
