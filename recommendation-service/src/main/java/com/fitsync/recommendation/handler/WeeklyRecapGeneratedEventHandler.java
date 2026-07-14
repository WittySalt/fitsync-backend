package com.fitsync.recommendation.handler;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.service.ai.WeeklyNarrativeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reuses the analytics weekly recap as the recent-training summary (R2 weekly adherence,
 * the A1 narrative inputs) — consume, don't recompute. Only a recap for a week at
 * or after the tracked one replaces the summary (out-of-order recaps are ignored).
 * The recap is also the A1 weekly cadence: it enqueues (never runs) narrative
 * generation — this is the sanctioned exception to "no AI from events" because it
 * fires once per user per week, asynchronously, behind the change-detection gate.
 */
@Service
public class WeeklyRecapGeneratedEventHandler implements DomainEventConsumer<WeeklyRecapGeneratedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;
    private final WeeklyNarrativeService weeklyNarrativeService;

    public WeeklyRecapGeneratedEventHandler(RecommendationContextStore contextStore,
                                            ProcessedEventGuard processedEventGuard,
                                            WeeklyNarrativeService weeklyNarrativeService) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
        this.weeklyNarrativeService = weeklyNarrativeService;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<WeeklyRecapGeneratedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        WeeklyRecapGeneratedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        boolean replaces = payload.weekStart() == null
                || context.getRecentWeekStart() == null
                || !payload.weekStart().isBefore(context.getRecentWeekStart());
        if (replaces) {
            context.setRecentWorkoutCount(payload.workoutCount());
            context.setRecentWorkoutMinutes(payload.workoutMinutes());
            context.setRecentActiveDays(payload.activeDays());
            context.setRecentWeekStart(payload.weekStart());
        }
        contextStore.saveStale(context);
        processedEventGuard.record(event);
        weeklyNarrativeService.generateAsync(event.userId());
    }
}
