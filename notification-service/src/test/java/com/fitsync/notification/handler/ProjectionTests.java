package com.fitsync.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.GoalEntity;
import com.fitsync.notification.entity.PlanDayEntity;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.support.NotificationTestBase;

class ProjectionTests extends NotificationTestBase {

    @Test
    void userRegisteredStoresAddressAndName() {
        register("  ada@example.com ", "Ada");

        RecipientEntity recipient = recipientRepository.findById(userId).orElseThrow();
        assertThat(recipient.getEmail()).isEqualTo("ada@example.com");
        assertThat(recipient.getDisplayName()).isEqualTo("Ada");
        assertThat(recipient.isEmailEnabled()).isFalse();
    }

    @Test
    void preferencesReplaceEverySettingAndKeepTheAddress() {
        register("ada@example.com", "Ada");
        preferences(true, "22:00", "07:00", NEW_YORK, "18:30", "email");

        RecipientEntity recipient = recipientRepository.findById(userId).orElseThrow();
        assertThat(recipient.getEmail()).isEqualTo("ada@example.com");
        assertThat(recipient.isEmailEnabled()).isTrue();
        assertThat(recipient.isWorkoutReminderEnabled()).isTrue();
        assertThat(recipient.getWeeklyReportChannel()).isEqualTo("EMAIL");
        assertThat(recipient.getQuietHoursStart()).isEqualTo(LocalTime.of(22, 0));
        assertThat(recipient.getQuietHoursEnd()).isEqualTo(LocalTime.of(7, 0));
        assertThat(recipient.getTimezone()).isEqualTo(NEW_YORK);
        assertThat(recipient.getPreferredWorkoutTime()).isEqualTo(LocalTime.of(18, 30));

        preferencesUpdated.consume(event("NotificationPreferencesUpdated",
                new NotificationPreferencesUpdatedPayload(false, false, false, false, null, null, "bad", null,
                        null)));
        recipient = recipientRepository.findById(userId).orElseThrow();
        assertThat(recipient.isEmailEnabled()).isFalse();
        assertThat(recipient.getQuietHoursStart()).isNull();
        assertThat(recipient.getQuietHoursEnd()).isNull();
        assertThat(recipient.getPreferredWorkoutTime()).isNull();
        assertThat(recipient.getTimezone()).isNull();
    }

    @Test
    void deletionRequestMarksPendingAndCancellationClearsIt() {
        register("ada@example.com", "Ada");
        Instant scheduledFor = Instant.parse("2026-11-06T00:00:00Z");
        requestDeletion(clock.instant(), scheduledFor);
        requestDeletion(clock.instant(), scheduledFor);

        assertThat(recipientRepository.findById(userId).orElseThrow().getDeletionScheduledFor())
                .isEqualTo(scheduledFor);
        assertThat(emails(EmailKind.DELETION_SCHEDULED)).hasSize(1);

        cancelDeletion();
        assertThat(recipientRepository.findById(userId).orElseThrow().getDeletionScheduledFor()).isNull();
    }

    @Test
    void goalTakesItsStartFromTheLatestEarlierWeightOnFirstSightOnly() {
        weight("82.4", Instant.parse("2026-10-01T07:00:00Z"));
        weight("81.9", Instant.parse("2026-10-05T07:00:00Z"));
        clock.set("2026-10-06T09:00:00Z");
        UUID goalId = goal("weight", "78", "active");

        GoalEntity goal = goalRepository.findById(goalId).orElseThrow();
        assertThat(goal.getGoalType()).isEqualTo("WEIGHT");
        assertThat(goal.getStatus()).isEqualTo("ACTIVE");
        assertThat(goal.getStartWeightKg()).isEqualByComparingTo("81.9");
        assertThat(goal.getStartedAt()).isEqualTo(Instant.parse("2026-10-06T09:00:00Z"));

        clock.set("2026-10-07T09:00:00Z");
        goal(goalId, "WEIGHT", "77", "ACTIVE");
        goal = goalRepository.findById(goalId).orElseThrow();
        assertThat(goal.getTargetValue()).isEqualByComparingTo("77");
        assertThat(goal.getStartWeightKg()).isEqualByComparingTo("81.9");
        assertThat(goal.getStartedAt()).isEqualTo(Instant.parse("2026-10-06T09:00:00Z"));
    }

    @Test
    void weightIsStoredOnceAndFillsAMissingGoalStart() {
        UUID goalId = goal("WEIGHT", "70", "ACTIVE");
        assertThat(goalRepository.findById(goalId).orElseThrow().getStartWeightKg()).isNull();

        weight("74.2", clock.instant());

        assertThat(weightRepository.findAll()).hasSize(1);
        assertThat(goalRepository.findById(goalId).orElseThrow().getStartWeightKg()).isEqualByComparingTo("74.2");
    }

    @Test
    void bodyMetricsWithoutWeightAreIgnored() {
        bodyMetricsRecorded.consume(event("BodyMetricsRecorded",
                new com.fitsync.contracts.events.user.BodyMetricsRecordedPayload(UUID.randomUUID(), null,
                        new BigDecimal("20"), null, clock.instant())));
        assertThat(weightRepository.findAll()).isEmpty();
    }

    @Test
    void workoutLoggedKeepsTheLatestTimeAndIgnoresCancelledSessions() {
        Instant later = Instant.parse("2026-10-06T19:00:00Z");
        workoutAt(later);
        workoutAt(Instant.parse("2026-10-05T19:00:00Z"));
        assertThat(recipientRepository.findById(userId).orElseThrow().getLastWorkoutAt()).isEqualTo(later);

        workoutLogged.consume(event("WorkoutLogged", new WorkoutLoggedPayload(UUID.randomUUID(), null, "STRENGTH",
                "CANCELLED", null, Instant.parse("2026-10-07T19:00:00Z"), 0, 0, 0, "MANUAL")));
        assertThat(recipientRepository.findById(userId).orElseThrow().getLastWorkoutAt()).isEqualTo(later);

        // Without a completion time the start counts, and without either the event time does.
        workoutLogged.consume(event("WorkoutLogged", new WorkoutLoggedPayload(UUID.randomUUID(), null, "STRENGTH",
                null, Instant.parse("2026-10-06T20:00:00Z"), null, 0, 0, 0, "MANUAL")));
        assertThat(recipientRepository.findById(userId).orElseThrow().getLastWorkoutAt())
                .isEqualTo(Instant.parse("2026-10-06T20:00:00Z"));
    }

    @Test
    void planScheduleIsReplacedWholeAndEmptiedWithoutAnActivePlan() {
        plan(day(1, "strength", "Upper body", 45, 6), day(3, "CARDIO", "Run", 30, 1), day(9, "REST", "x", null, 0));
        assertThat(planDayRepository.findAll()).extracting(PlanDayEntity::getDayOfWeek)
                .containsExactlyInAnyOrder(1, 3);
        assertThat(planDayRepository.findByUserIdAndDayOfWeek(userId, 1).orElseThrow().getKind())
                .isEqualTo("STRENGTH");

        plan(day(2, "SPORT", "Badminton", 90, 0));
        assertThat(planDayRepository.findAll()).extracting(PlanDayEntity::getDayOfWeek).containsExactly(2);

        plan();
        assertThat(planDayRepository.findAll()).isEmpty();
    }

    @Test
    void weeklyRecapQueuesOneReportPerWeek() {
        register("ada@example.com", "Ada");
        recap("2026-09-28", "2026-10-04", 3, 120, 0, 3);
        recap("2026-09-28", "2026-10-04", 3, 120, 0, 3);
        assertThat(emails(EmailKind.WEEKLY_REPORT)).hasSize(1);
    }

    @Test
    void accountDeletedPurgesEveryRowOfTheUserOnly() {
        UUID otherUser = UUID.randomUUID();
        UUID self = userId;
        register("ada@example.com", "Ada");
        allOn(NEW_YORK, "18:00");
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        goal("WEIGHT", "70", "ACTIVE");
        weight("74", clock.instant());
        requestDeletion(clock.instant(), Instant.parse("2026-11-06T00:00:00Z"));

        userId = otherUser;
        register("bob@example.com", "Bob");
        weight("90", clock.instant());

        userId = self;
        deleteAccount();

        assertThat(recipientRepository.findById(self)).isEmpty();
        assertThat(recipientRepository.findById(otherUser)).isPresent();
        assertThat(emailRepository.findAll()).allMatch(email -> !email.getUserId().equals(self));
        assertThat(planDayRepository.findAll()).allMatch(day -> !day.getUserId().equals(self));
        assertThat(goalRepository.findAll()).allMatch(goal -> !goal.getUserId().equals(self));
        assertThat(weightRepository.findAll()).hasSize(1)
                .allMatch(weight -> weight.getUserId().equals(otherUser));
    }
}
