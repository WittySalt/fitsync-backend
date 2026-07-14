package com.fitsync.recommendation.handler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the running "today" nutrition totals for the R1 daily-gap rule. A meal on a
 * new (later) day resets the totals; a meal on the tracked day accumulates; a meal on an
 * earlier day only marks the context stale (historical back-fill is not re-aggregated —
 * per {@code analytics-collaboration.md} §3 the authoritative daily aggregate lives in
 * analytics; this is a cheap local projection).
 */
@Service
public class MealLoggedEventHandler implements DomainEventConsumer<MealLoggedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;
    private final Clock clock;

    public MealLoggedEventHandler(RecommendationContextStore contextStore,
                                  ProcessedEventGuard processedEventGuard,
                                  Clock clock) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<MealLoggedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        MealLoggedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        LocalDate mealDate = resolveMealDate(event, payload, context);
        if (context.getTodayDate() == null || mealDate.isAfter(context.getTodayDate())) {
            context.setTodayDate(mealDate);
            context.setTodayCaloriesConsumed(payload.totalCalories());
            context.setTodayProteinGrams(payload.totalProteinGrams());
        } else if (mealDate.equals(context.getTodayDate())) {
            context.setTodayCaloriesConsumed(
                    addCalories(context.getTodayCaloriesConsumed(), payload.totalCalories()));
            context.setTodayProteinGrams(
                    addGrams(context.getTodayProteinGrams(), payload.totalProteinGrams()));
        }
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }

    private LocalDate resolveMealDate(FitSyncDomainEvent<MealLoggedPayload> event,
                                      MealLoggedPayload payload,
                                      RecommendationContextEntity context) {
        Instant consumedAt = payload.consumedAt() != null ? payload.consumedAt() : event.occurredAt();
        ZoneId zone = UserTimeZones.resolve(context.getTimezone());
        if (consumedAt == null) {
            return LocalDate.now(clock.withZone(zone));
        }
        return consumedAt.atZone(zone).toLocalDate();
    }

    private static Integer addCalories(Integer current, Integer increment) {
        if (increment == null) {
            return current;
        }
        if (current == null) {
            return increment;
        }
        return Integer.valueOf(current.intValue() + increment.intValue());
    }

    private static BigDecimal addGrams(BigDecimal current, BigDecimal increment) {
        if (increment == null) {
            return current;
        }
        if (current == null) {
            return increment;
        }
        return current.add(increment);
    }
}
