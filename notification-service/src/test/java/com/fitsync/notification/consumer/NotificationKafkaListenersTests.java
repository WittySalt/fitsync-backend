package com.fitsync.notification.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.AccountDeletionCancelledPayload;
import com.fitsync.contracts.events.user.AccountDeletionRequestedPayload;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload;

/**
 * Every listener must hand its own event type to the consumer and skip the other event types that
 * share its topic.
 */
@SuppressWarnings("unchecked")
class NotificationKafkaListenersTests {

    @FunctionalInterface
    private interface MessageEndpoint {
        void onMessage(String payload, String topic, String key);
    }

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Instant at = Instant.parse("2026-10-07T12:00:00Z");

    @Test
    void userEventListeners() throws Exception {
        DomainEventConsumer<UserRegisteredPayload> registered = mock(DomainEventConsumer.class);
        check(registered, new UserRegisteredKafkaListener(objectMapper, registered)::onMessage, "UserRegistered",
                new UserRegisteredPayload("ada@example.com", "Ada", "USER", at));

        DomainEventConsumer<NotificationPreferencesUpdatedPayload> preferences = mock(DomainEventConsumer.class);
        check(preferences, new NotificationPreferencesUpdatedKafkaListener(objectMapper, preferences)::onMessage,
                "NotificationPreferencesUpdated", new NotificationPreferencesUpdatedPayload(true, true, true, true,
                        "EMAIL", "22:00", "07:00", "Asia/Singapore", "18:00"));

        DomainEventConsumer<AccountDeletionRequestedPayload> requested = mock(DomainEventConsumer.class);
        check(requested, new AccountDeletionRequestedKafkaListener(objectMapper, requested)::onMessage,
                "AccountDeletionRequested", new AccountDeletionRequestedPayload(at, at.plusSeconds(86_400)));

        DomainEventConsumer<AccountDeletionCancelledPayload> cancelled = mock(DomainEventConsumer.class);
        check(cancelled, new AccountDeletionCancelledKafkaListener(objectMapper, cancelled)::onMessage,
                "AccountDeletionCancelled", new AccountDeletionCancelledPayload(at));

        DomainEventConsumer<AccountDeletedPayload> deleted = mock(DomainEventConsumer.class);
        check(deleted, new AccountDeletedKafkaListener(objectMapper, deleted)::onMessage, "AccountDeleted",
                new AccountDeletedPayload(at, "USER_REQUEST"));
    }

    @Test
    void progressEventListeners() throws Exception {
        DomainEventConsumer<GoalUpdatedPayload> goal = mock(DomainEventConsumer.class);
        check(goal, new GoalUpdatedKafkaListener(objectMapper, goal)::onMessage, "GoalUpdated",
                new GoalUpdatedPayload(UUID.randomUUID(), "WEIGHT", new BigDecimal("75"), "kg", null, "ACTIVE"));

        DomainEventConsumer<BodyMetricsRecordedPayload> body = mock(DomainEventConsumer.class);
        check(body, new BodyMetricsRecordedKafkaListener(objectMapper, body)::onMessage, "BodyMetricsRecorded",
                new BodyMetricsRecordedPayload(UUID.randomUUID(), new BigDecimal("80"), null, null, at));
    }

    @Test
    void workoutAndAnalyticsEventListeners() throws Exception {
        DomainEventConsumer<WorkoutLoggedPayload> logged = mock(DomainEventConsumer.class);
        check(logged, new WorkoutLoggedKafkaListener(objectMapper, logged)::onMessage, "WorkoutLogged",
                new WorkoutLoggedPayload(UUID.randomUUID(), null, "STRENGTH", "COMPLETED", at, at, 60, 300, 5,
                        "MANUAL"));

        DomainEventConsumer<WorkoutPlanScheduleChangedPayload> schedule = mock(DomainEventConsumer.class);
        check(schedule, new WorkoutPlanScheduleChangedKafkaListener(objectMapper, schedule)::onMessage,
                "WorkoutPlanScheduleChanged", new WorkoutPlanScheduleChangedPayload(UUID.randomUUID(), "Plan",
                        List.of(new WorkoutPlanScheduleChangedPayload.PlannedDay(1, "STRENGTH", "Upper", 45, 6)),
                        at));

        DomainEventConsumer<WeeklyRecapGeneratedPayload> recap = mock(DomainEventConsumer.class);
        check(recap, new WeeklyRecapGeneratedKafkaListener(objectMapper, recap)::onMessage, "WeeklyRecapGenerated",
                new WeeklyRecapGeneratedPayload(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), 3, 135,
                        900, 12_400, 4));
    }

    private <T> void check(DomainEventConsumer<T> consumer, MessageEndpoint endpoint, String eventType, T payload)
            throws Exception {
        UUID userId = UUID.randomUUID();
        String matching = objectMapper.writeValueAsString(
                new FitSyncDomainEvent<>(UUID.randomUUID(), eventType, 1, at, "test", userId, payload));
        String other = objectMapper.writeValueAsString(
                new FitSyncDomainEvent<>(UUID.randomUUID(), "SomethingElse", 1, at, "test", userId, payload));

        endpoint.onMessage(other, "topic", userId.toString());
        endpoint.onMessage(matching, "topic", userId.toString());

        verify(consumer, times(1)).consume(any());
    }
}
