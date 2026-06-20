package com.fitsync.workout.controller;

import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.OutboxRelayResponse;
import com.fitsync.workout.messaging.OutboxEventRelay;
import com.fitsync.workout.service.ExerciseCatalogImportService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
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
    private final ExerciseCatalogImportService exerciseCatalogImportService;

    public InternalMessagingController(OutboxEventRelay outboxEventRelay,
                                       ExerciseCatalogImportService exerciseCatalogImportService) {
        this.outboxEventRelay = outboxEventRelay;
        this.exerciseCatalogImportService = exerciseCatalogImportService;
    }

    @PostMapping("/outbox/publish")
    public ResponseEntity<OutboxRelayResponse> publishPendingBatch(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int batchSize) {
        int publishedCount = outboxEventRelay.publishPendingBatch(batchSize);
        return ResponseEntity.ok(new OutboxRelayResponse(batchSize, publishedCount));
    }

    @Operation(summary = "Import bundled free-exercise-db catalog exercises")
    @PostMapping("/workouts/exercises/import-catalog")
    public ExerciseCatalogImportResponse importExerciseCatalog() {
        return exerciseCatalogImportService.importFreeExerciseDbCatalog();
    }
}
