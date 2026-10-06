package com.fitsync.recommendation.handler;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.recommendation.domain.FoodGroupCalories;
import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the day of the latest logged meal (the logging-reminder rule's input) and the
 * week's food-group balance, and marks the context stale so cached recommendations refresh.
 *
 * <p>It keeps no running calorie or protein total. A sum can only grow, a deleted or edited
 * meal cannot be taken back out of it, and the coach once quoted 4910 kcal for a diary that
 * held 476. Today's intake is read from the Nutrition Diary's daily summary when the facts are
 * assembled; the {@code today_calories_consumed} and {@code today_protein_grams} columns are
 * no longer written.
 */
@Service
public class MealLoggedEventHandler implements DomainEventConsumer<MealLoggedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MealLoggedEventHandler(RecommendationContextStore contextStore,
                                  ProcessedEventGuard processedEventGuard,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
        this.objectMapper = objectMapper;
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
        }
        accumulateWeekFoodGroups(context, mealDate, payload);
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }

    /**
     * A meal in a new (later) week restarts the food-group accumulation, a meal in the
     * tracked week merges into it, and a historical meal is ignored.
     */
    private void accumulateWeekFoodGroups(RecommendationContextEntity context,
                                          LocalDate mealDate,
                                          MealLoggedPayload payload) {
        LocalDate mealWeekStart = mealDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (context.getNutritionWeekStart() == null || mealWeekStart.isAfter(context.getNutritionWeekStart())) {
            context.setNutritionWeekStart(mealWeekStart);
            context.setWeekFoodGroupCaloriesJson(
                    FoodGroupCalories.write(objectMapper, payload.foodGroupCalories()));
        } else if (mealWeekStart.equals(context.getNutritionWeekStart())
                && !payload.foodGroupCalories().isEmpty()) {
            context.setWeekFoodGroupCaloriesJson(FoodGroupCalories.write(objectMapper,
                    FoodGroupCalories.merge(
                            FoodGroupCalories.read(objectMapper, context.getWeekFoodGroupCaloriesJson()),
                            payload.foodGroupCalories())));
        }
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
}
