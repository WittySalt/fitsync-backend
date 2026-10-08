package com.fitsync.notification.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

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
import com.fitsync.contracts.events.workout.WorkoutPlanScheduleChangedPayload.PlannedDay;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.handler.AccountDeletedHandler;
import com.fitsync.notification.handler.AccountDeletionCancelledHandler;
import com.fitsync.notification.handler.AccountDeletionRequestedHandler;
import com.fitsync.notification.handler.BodyMetricsRecordedHandler;
import com.fitsync.notification.handler.GoalUpdatedHandler;
import com.fitsync.notification.handler.NotificationPreferencesUpdatedHandler;
import com.fitsync.notification.handler.UserRegisteredHandler;
import com.fitsync.notification.handler.WeeklyRecapGeneratedHandler;
import com.fitsync.notification.handler.WorkoutLoggedHandler;
import com.fitsync.notification.handler.WorkoutPlanScheduleChangedHandler;
import com.fitsync.notification.repository.EmailRepository;
import com.fitsync.notification.repository.GoalRepository;
import com.fitsync.notification.repository.PlanDayRepository;
import com.fitsync.notification.repository.RecipientRepository;
import com.fitsync.notification.repository.WeightRepository;
import com.fitsync.notification.service.NotificationTick;

/**
 * Spring context on H2 with a hand-moved clock and a recording sender. Events go straight into the
 * handlers, which is what the Kafka listeners do once a message is read.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationTestBase.TestBeans.class)
public abstract class NotificationTestBase {

    protected static final String NEW_YORK = "America/New_York";

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    @Autowired protected MutableClock clock;
    @Autowired protected RecordingEmailSender sender;
    @Autowired protected NotificationTick tick;

    @Autowired protected RecipientRepository recipientRepository;
    @Autowired protected EmailRepository emailRepository;
    @Autowired protected GoalRepository goalRepository;
    @Autowired protected WeightRepository weightRepository;
    @Autowired protected PlanDayRepository planDayRepository;

    @Autowired protected UserRegisteredHandler userRegistered;
    @Autowired protected NotificationPreferencesUpdatedHandler preferencesUpdated;
    @Autowired protected AccountDeletionRequestedHandler deletionRequested;
    @Autowired protected AccountDeletionCancelledHandler deletionCancelled;
    @Autowired protected AccountDeletedHandler accountDeleted;
    @Autowired protected GoalUpdatedHandler goalUpdated;
    @Autowired protected BodyMetricsRecordedHandler bodyMetricsRecorded;
    @Autowired protected WorkoutLoggedHandler workoutLogged;
    @Autowired protected WorkoutPlanScheduleChangedHandler planScheduleChanged;
    @Autowired protected WeeklyRecapGeneratedHandler weeklyRecapGenerated;

    protected UUID userId;

    @BeforeEach
    void resetState() {
        emailRepository.deleteAll();
        planDayRepository.deleteAll();
        goalRepository.deleteAll();
        weightRepository.deleteAll();
        recipientRepository.deleteAll();
        sender.reset();
        clock.set("2026-10-07T00:00:00Z");
        userId = UUID.randomUUID();
    }

    protected <T> FitSyncDomainEvent<T> event(String type, T payload) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), type, 1, clock.instant(), "test", userId, payload);
    }

    protected void register(String email, String displayName) {
        userRegistered.consume(event("UserRegistered",
                new UserRegisteredPayload(email, displayName, "USER", clock.instant())));
    }

    /** Email on with every toggle on, the given timezone and training time, and no quiet hours. */
    protected void allOn(String timezone, String preferredTime) {
        preferences(true, null, null, timezone, preferredTime, "EMAIL");
    }

    protected void preferences(boolean emailEnabled, String quietStart, String quietEnd, String timezone,
                               String preferredTime, String channel) {
        preferencesUpdated.consume(event("NotificationPreferencesUpdated",
                new NotificationPreferencesUpdatedPayload(emailEnabled, true, true, true, channel,
                        quietStart, quietEnd, timezone, preferredTime)));
    }

    protected void plan(PlannedDay... days) {
        planScheduleChanged.consume(event("WorkoutPlanScheduleChanged",
                new WorkoutPlanScheduleChangedPayload(days.length == 0 ? null : UUID.randomUUID(), "Plan",
                        List.of(days), clock.instant())));
    }

    protected static PlannedDay day(int dayOfWeek, String kind, String title, Integer minutes, int exercises) {
        return new PlannedDay(dayOfWeek, kind, title, minutes, exercises);
    }

    protected void workoutAt(Instant completedAt) {
        workoutLogged.consume(event("WorkoutLogged", new WorkoutLoggedPayload(UUID.randomUUID(), null, "STRENGTH",
                "COMPLETED", completedAt.minusSeconds(3600), completedAt, 60, 300, 5, "MANUAL")));
    }

    protected UUID goal(String type, String target, String status) {
        UUID goalId = UUID.randomUUID();
        goal(goalId, type, target, status);
        return goalId;
    }

    protected void goal(UUID goalId, String type, String target, String status) {
        goalUpdated.consume(event("GoalUpdated", new GoalUpdatedPayload(goalId, type,
                target == null ? null : new BigDecimal(target), "kg", null, status)));
    }

    protected void weight(String kg, Instant measuredAt) {
        bodyMetricsRecorded.consume(event("BodyMetricsRecorded", new BodyMetricsRecordedPayload(UUID.randomUUID(),
                new BigDecimal(kg), null, null, measuredAt)));
    }

    protected void recap(String weekStart, String weekEnd, int workouts, int minutes, int calories, int activeDays) {
        weeklyRecapGenerated.consume(event("WeeklyRecapGenerated", new WeeklyRecapGeneratedPayload(
                java.time.LocalDate.parse(weekStart), java.time.LocalDate.parse(weekEnd), workouts, minutes, 0,
                calories, activeDays)));
    }

    protected void requestDeletion(Instant requestedAt, Instant scheduledFor) {
        deletionRequested.consume(event("AccountDeletionRequested",
                new AccountDeletionRequestedPayload(requestedAt, scheduledFor)));
    }

    protected void cancelDeletion() {
        deletionCancelled.consume(event("AccountDeletionCancelled",
                new AccountDeletionCancelledPayload(clock.instant())));
    }

    protected void deleteAccount() {
        accountDeleted.consume(event("AccountDeleted", new AccountDeletedPayload(clock.instant(), "USER_REQUEST")));
    }

    protected List<EmailEntity> emails(EmailKind kind) {
        return emailRepository.findAll().stream()
                .filter(email -> email.getUserId().equals(userId) && email.getKind() == kind)
                .toList();
    }

    protected EmailEntity onlyEmail(EmailKind kind) {
        List<EmailEntity> found = emails(kind);
        if (found.size() != 1) {
            throw new AssertionError("Expected one " + kind + " email but found " + found.size());
        }
        return found.get(0);
    }

    protected EmailStatus statusOf(EmailKind kind) {
        return onlyEmail(kind).getStatus();
    }
}
