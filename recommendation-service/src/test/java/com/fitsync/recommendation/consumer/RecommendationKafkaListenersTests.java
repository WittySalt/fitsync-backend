package com.fitsync.recommendation.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import org.junit.jupiter.api.Test;

/**
 * Every listener must dispatch its own event type to the consumer and silently skip
 * other event types sharing the topic.
 */
class RecommendationKafkaListenersTests {

    @FunctionalInterface
    private interface MessageEndpoint {
        void onMessage(String payload, String topic, String key);
    }

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void goalUpdatedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<GoalUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        GoalUpdatedKafkaListener listener = new GoalUpdatedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "GoalUpdated",
                new GoalUpdatedPayload(UUID.randomUUID(), "WEIGHT", new BigDecimal("75"), "kg",
                        LocalDate.parse("2026-10-01"), "ACTIVE"));
    }

    @Test
    void bodyMetricsRecordedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<BodyMetricsRecordedPayload> consumer = mock(DomainEventConsumer.class);
        BodyMetricsRecordedKafkaListener listener = new BodyMetricsRecordedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "BodyMetricsRecorded",
                new BodyMetricsRecordedPayload(UUID.randomUUID(), new BigDecimal("82.5"),
                        new BigDecimal("21"), null, Instant.parse("2026-07-05T08:00:00Z")));
    }

    @Test
    void mealLoggedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<MealLoggedPayload> consumer = mock(DomainEventConsumer.class);
        MealLoggedKafkaListener listener = new MealLoggedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "MealLogged",
                new MealLoggedPayload(UUID.randomUUID(), "LUNCH", Instant.parse("2026-07-06T12:00:00Z"),
                        650, new BigDecimal("40.5"), new BigDecimal("50"), new BigDecimal("20"), 2, "MANUAL"));
    }

    @Test
    void mealUpdatedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<MealUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        MealUpdatedKafkaListener listener = new MealUpdatedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "MealUpdated",
                new MealUpdatedPayload(UUID.randomUUID(), "LUNCH", Instant.parse("2026-07-06T12:00:00Z"),
                        500, new BigDecimal("30"), new BigDecimal("40"), new BigDecimal("15"), 2, "MANUAL"));
    }

    @Test
    void mealDeletedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<MealDeletedPayload> consumer = mock(DomainEventConsumer.class);
        MealDeletedKafkaListener listener = new MealDeletedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "MealDeleted",
                new MealDeletedPayload(UUID.randomUUID(), Instant.parse("2026-07-06T13:00:00Z")));
    }

    @Test
    void userRegisteredListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<UserRegisteredPayload> consumer = mock(DomainEventConsumer.class);
        UserRegisteredKafkaListener listener = new UserRegisteredKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "UserRegistered",
                new UserRegisteredPayload("user@example.com", "User", "USER",
                        Instant.parse("2026-07-06T10:00:00Z")));
    }

    @Test
    void userProfileUpdatedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<UserProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserProfileUpdatedKafkaListener listener = new UserProfileUpdatedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "UserProfileUpdated",
                new UserProfileUpdatedPayload("User", new BigDecimal("181.5"), "MODERATE", true,
                        List.of("heightCm"), "Europe/Berlin"));
    }

    @Test
    void userPreferencesUpdatedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<UserPreferencesUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserPreferencesUpdatedKafkaListener listener =
                new UserPreferencesUpdatedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "UserPreferencesUpdated",
                new UserPreferencesUpdatedPayload("METRIC", 4, "VEGETARIAN", true,
                        List.of("DUMBBELLS"), List.of("CHEST"), "Asia/Shanghai"));
    }

    @Test
    void weeklyRecapGeneratedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<WeeklyRecapGeneratedPayload> consumer = mock(DomainEventConsumer.class);
        WeeklyRecapGeneratedKafkaListener listener = new WeeklyRecapGeneratedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "WeeklyRecapGenerated",
                new WeeklyRecapGeneratedPayload(LocalDate.parse("2026-06-29"), LocalDate.parse("2026-07-05"),
                        4, 180, 1500, 12000, 4));
    }

    @Test
    void streakMilestoneReachedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<StreakMilestoneReachedPayload> consumer = mock(DomainEventConsumer.class);
        StreakMilestoneReachedKafkaListener listener =
                new StreakMilestoneReachedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "StreakMilestoneReached",
                new StreakMilestoneReachedPayload(7, 7, LocalDate.parse("2026-07-05")));
    }

    @Test
    void personalRecordAchievedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<PersonalRecordAchievedPayload> consumer = mock(DomainEventConsumer.class);
        PersonalRecordAchievedKafkaListener listener =
                new PersonalRecordAchievedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "PersonalRecordAchieved",
                new PersonalRecordAchievedPayload(UUID.randomUUID(), "Barbell Squat", UUID.randomUUID(),
                        List.of(new PersonalRecordAchievedPayload.RecordAchievement(
                                "MAX_WEIGHT_KG", new BigDecimal("120.00"))),
                        Instant.parse("2026-07-05T18:00:00Z")));
    }

    @Test
    void accountDeletedListenerDispatchesAndFilters() throws Exception {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        AccountDeletedKafkaListener listener = new AccountDeletedKafkaListener(objectMapper, consumer);
        verifyDispatchAndFiltering(consumer, listener::onMessage, "AccountDeleted",
                new AccountDeletedPayload(Instant.parse("2026-07-06T10:00:00Z"), "USER_REQUEST"));
    }

    private <T> void verifyDispatchAndFiltering(DomainEventConsumer<T> consumer,
                                                MessageEndpoint endpoint,
                                                String matchingEventType,
                                                T payload) throws Exception {
        FitSyncDomainEvent<T> matching = event(matchingEventType, payload);
        endpoint.onMessage(objectMapper.writeValueAsString(matching), "topic", matching.userId().toString());
        verify(consumer).consume(matching);

        FitSyncDomainEvent<T> other = event("UnrelatedEventType", payload);
        endpoint.onMessage(objectMapper.writeValueAsString(other), "topic", other.userId().toString());
        verifyNoMoreInteractions(consumer);
    }

    private static <T> FitSyncDomainEvent<T> event(String eventType, T payload) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(), eventType, 1, Instant.parse("2026-07-06T12:00:00Z"),
                "test", UUID.randomUUID(), payload);
    }
}
