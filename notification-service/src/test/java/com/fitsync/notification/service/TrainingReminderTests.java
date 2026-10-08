package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.email.OutgoingEmail;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.support.NotificationTestBase;

/** E1. 7 October 2026 is a Wednesday, and New York is four hours behind UTC that day. */
class TrainingReminderTests extends NotificationTestBase {

    private static final int WEDNESDAY = 3;

    @BeforeEach
    void user() {
        register("ada@example.com", "Ada");
    }

    @Test
    void sendsAnHourBeforeTheTrainingTimeInTheUsersTimezone() {
        allOn(NEW_YORK, "18:00");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        clock.set("2026-10-07T20:59:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();

        clock.set("2026-10-07T21:00:00Z");
        tick.run();

        assertThat(sender.sent()).hasSize(1);
        OutgoingEmail email = sender.sent().get(0);
        assertThat(email.to()).isEqualTo("ada@example.com");
        assertThat(email.subject()).isEqualTo("Today: Upper body · 45 min at 18:00");
        assertThat(email.bodyText())
                .contains("Hi Ada,")
                .contains("Exercises: 6")
                .contains("Open today's session:\nhttps://app.fitsync.test/workout")
                .contains("You get this because Workout reminders are on.")
                .contains("https://app.fitsync.test/settings?section=notifications");
        EmailEntity record = onlyEmail(EmailKind.TRAINING_TODAY);
        assertThat(record.getDedupeKey()).isEqualTo("2026-10-07");
        assertThat(record.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(record.getExpiresAt()).isEqualTo(Instant.parse("2026-10-07T22:00:00Z"));
    }

    @Test
    void sendsAtEightWithoutATrainingTimeAndLeavesUnknownPartsOutOfTheSubject() {
        allOn(NEW_YORK, null);
        plan(day(WEDNESDAY, "CARDIO", "Easy run", null, 1));

        clock.set("2026-10-07T11:59:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();

        clock.set("2026-10-07T12:00:00Z");
        tick.run();
        assertThat(sender.sent()).singleElement()
                .extracting(OutgoingEmail::subject).isEqualTo("Today: Easy run");
        assertThat(onlyEmail(EmailKind.TRAINING_TODAY).getExpiresAt())
                .isEqualTo(Instant.parse("2026-10-08T04:00:00Z"));
    }

    @Test
    void trainingJustAfterMidnightSendsAtMidnight() {
        allOn(NEW_YORK, "00:30");
        plan(day(WEDNESDAY, "STRENGTH", "Early lift", 30, 4));

        clock.set("2026-10-07T04:00:00Z");
        tick.run();
        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    void sendsOnlyOncePerDay() {
        allOn(NEW_YORK, "18:00");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        clock.advance(Duration.ofMinutes(5));
        tick.run();
        clock.advance(Duration.ofMinutes(5));
        tick.run();

        assertThat(sender.sent()).hasSize(1);
        assertThat(emails(EmailKind.TRAINING_TODAY)).hasSize(1);
    }

    @Test
    void nothingOnARestDay() {
        allOn(NEW_YORK, "18:00");
        plan(day(WEDNESDAY, "REST", "Rest", null, 0));

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();
    }

    @Test
    void nothingWithoutAnActivePlan() {
        allOn(NEW_YORK, "18:00");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));
        plan();

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();
    }

    @Test
    void nothingWhenAWorkoutWasLoggedTodayInTheUsersTimezone() {
        allOn(NEW_YORK, "18:00");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));
        // 08:00 in New York on the same Wednesday.
        workoutAt(Instant.parse("2026-10-07T12:00:00Z"));

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();
    }

    @Test
    void aWorkoutOnTheLocalDayBeforeDoesNotCount() {
        allOn(NEW_YORK, "18:00");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));
        // 7 October 02:00 UTC is still Tuesday evening in New York.
        workoutAt(Instant.parse("2026-10-07T02:00:00Z"));

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    void aWorkoutLoggedAfterQueuingDropsTheReminder() {
        preferences(true, "06:00", "07:00", NEW_YORK, "07:30", "EMAIL");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        // 06:30 local is inside quiet hours, so the reminder waits until 07:00.
        clock.set("2026-10-07T10:30:00Z");
        tick.run();
        assertThat(statusOf(EmailKind.TRAINING_TODAY)).isEqualTo(EmailStatus.PENDING);
        assertThat(onlyEmail(EmailKind.TRAINING_TODAY).getDueAt()).isEqualTo(Instant.parse("2026-10-07T11:00:00Z"));

        workoutAt(Instant.parse("2026-10-07T10:50:00Z"));
        clock.set("2026-10-07T11:00:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.TRAINING_TODAY)).isEqualTo(EmailStatus.DROPPED);
    }

    @Test
    void quietHoursEndingBeforeTrainingDelayTheReminder() {
        preferences(true, "22:00", "07:00", NEW_YORK, "07:30", "EMAIL");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        clock.set("2026-10-07T10:30:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();

        clock.set("2026-10-07T11:00:00Z");
        tick.run();
        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    void droppedWhenQuietHoursOutlastTheTrainingTime() {
        preferences(true, "22:00", "07:30", NEW_YORK, "07:00", "EMAIL");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        clock.set("2026-10-07T10:00:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.TRAINING_TODAY)).isEqualTo(EmailStatus.DROPPED);

        clock.set("2026-10-07T11:30:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void nothingWhenEmailOrTheToggleIsOff() {
        preferences(false, null, null, NEW_YORK, "18:00", "EMAIL");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();
    }

    @Test
    void anUnknownTimezoneFallsBackToSingapore() {
        allOn("Mars/Olympus", "18:00");
        plan(day(WEDNESDAY, "STRENGTH", "Upper body", 45, 6));

        // 17:00 in Singapore is 09:00 UTC.
        clock.set("2026-10-07T09:00:00Z");
        tick.run();
        assertThat(sender.sent()).hasSize(1);
    }
}
