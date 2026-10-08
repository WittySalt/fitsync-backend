package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.email.OutgoingEmail;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.support.NotificationTestBase;

/** The rules every email shares: the master switch, deletion, E6's exemptions, retries and the footer. */
class DispatcherRulesTests extends NotificationTestBase {

    @Autowired
    private TrainingReminderPlanner planner;

    private static final Instant REMOVAL = Instant.parse("2026-11-06T03:00:00Z");

    @BeforeEach
    void user() {
        register("ada@example.com", "Ada");
    }

    @Test
    void deletionNoticeGoesOutWithEmailOffAndDuringQuietHours() {
        // 00:00 to 23:59 in New York covers the whole moment of the test.
        preferences(false, "00:00", "23:59", NEW_YORK, null, "EMAIL");
        clock.set("2026-10-07T14:00:00Z");

        requestDeletion(clock.instant(), REMOVAL);
        tick.run();

        assertThat(sender.sent()).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("Your account is scheduled for deletion");
            assertThat(email.bodyText())
                    .contains("scheduled for deletion on Thursday 5 November 2026")
                    .contains("sign in before then to cancel:\nhttps://app.fitsync.test/auth/login")
                    .contains("If this was not you, reset your password:\nhttps://app.fitsync.test/auth/login")
                    .contains("You get this because it is a security notice about your account.")
                    .contains("https://app.fitsync.test/settings?section=notifications");
        });
        assertThat(statusOf(EmailKind.DELETION_SCHEDULED)).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void deletionNoticeIsDroppedWithoutAnAddress() {
        userId = java.util.UUID.randomUUID();
        requestDeletion(clock.instant(), REMOVAL);
        tick.run();
        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.DELETION_SCHEDULED)).isEqualTo(EmailStatus.DROPPED);
    }

    @Test
    void masterSwitchOffDropsTrainingGoalAndWeeklyEmails() {
        queueOneOfEach();
        preferences(false, null, null, NEW_YORK, "18:00", "EMAIL");

        tick.run();

        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.GOAL_REACHED)).isEqualTo(EmailStatus.DROPPED);
        assertThat(statusOf(EmailKind.WEEKLY_REPORT)).isEqualTo(EmailStatus.DROPPED);
        assertThat(statusOf(EmailKind.TRAINING_TODAY)).isEqualTo(EmailStatus.DROPPED);
    }

    @Test
    void pendingDeletionDropsEverythingButTheDeletionNotice() {
        queueOneOfEach();
        requestDeletion(clock.instant(), REMOVAL);

        tick.run();

        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.DELETION_SCHEDULED);
        assertThat(statusOf(EmailKind.GOAL_REACHED)).isEqualTo(EmailStatus.DROPPED);
        assertThat(statusOf(EmailKind.WEEKLY_REPORT)).isEqualTo(EmailStatus.DROPPED);
        assertThat(statusOf(EmailKind.TRAINING_TODAY)).isEqualTo(EmailStatus.DROPPED);
    }

    @Test
    void noReminderIsPlannedWhileDeletionIsPendingAndOneIsAfterCancelling() {
        allOn(NEW_YORK, "18:00");
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        requestDeletion(clock.instant(), REMOVAL);
        tick.run();
        sender.reset();

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();

        cancelDeletion();
        clock.advance(Duration.ofMinutes(5));
        tick.run();
        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.TRAINING_TODAY);
    }

    @Test
    void accountDeletedPurgesQueuedEmailsSoNothingIsSent() {
        queueOneOfEach();
        deleteAccount();

        tick.run();

        assertThat(sender.sent()).isEmpty();
        assertThat(emailRepository.findAll()).isEmpty();
        assertThat(recipientRepository.findAll()).isEmpty();
    }

    @Test
    void sesFailureRetriesWithBackoffThenGivesUp() {
        allOn(NEW_YORK, null);
        clock.set("2026-10-07T12:00:00Z");
        requestDeletion(clock.instant(), REMOVAL);
        sender.failing(true);

        tick.run();
        EmailEntity email = onlyEmail(EmailKind.DELETION_SCHEDULED);
        assertThat(email.getAttempts()).isEqualTo(1);
        assertThat(email.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(email.getDueAt()).isEqualTo(Instant.parse("2026-10-07T12:01:00Z"));

        // Not due again until the backoff has passed.
        clock.set("2026-10-07T12:00:30Z");
        tick.run();
        assertThat(onlyEmail(EmailKind.DELETION_SCHEDULED).getAttempts()).isEqualTo(1);

        for (int attempt = 2; attempt <= EmailDispatcher.MAX_ATTEMPTS; attempt++) {
            clock.set(onlyEmail(EmailKind.DELETION_SCHEDULED).getDueAt());
            tick.run();
            assertThat(onlyEmail(EmailKind.DELETION_SCHEDULED).getAttempts()).isEqualTo(attempt);
        }
        assertThat(statusOf(EmailKind.DELETION_SCHEDULED)).isEqualTo(EmailStatus.FAILED);

        sender.failing(false);
        clock.advance(Duration.ofHours(1));
        tick.run();
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void aFailedAttemptThatLaterSucceedsIsSent() {
        allOn(NEW_YORK, null);
        requestDeletion(clock.instant(), REMOVAL);
        sender.failing(true);
        tick.run();

        sender.failing(false);
        clock.advance(Duration.ofMinutes(1));
        tick.run();
        assertThat(sender.sent()).hasSize(1);
        assertThat(statusOf(EmailKind.DELETION_SCHEDULED)).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void everyBodyCarriesItsReasonAndTheSettingsLinkAndNoPressure() {
        queueOneOfEach();
        tick.run();
        requestDeletion(clock.instant(), REMOVAL);
        tick.run();

        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactlyInAnyOrder(
                EmailKind.TRAINING_TODAY, EmailKind.GOAL_REACHED, EmailKind.WEEKLY_REPORT,
                EmailKind.DELETION_SCHEDULED);
        assertThat(sender.sent()).allSatisfy(email -> assertThat(email.bodyText())
                .startsWith("Hi Ada,")
                .contains("You get this because ")
                .contains("https://app.fitsync.test/settings?section=notifications")
                .doesNotContainIgnoringCase("missed")
                .doesNotContainIgnoringCase("streak"));
    }

    /**
     * Leaves one pending E1, E4 and E5 for the user, all due at 21:00 UTC on Wednesday 7 October
     * (17:00 in New York), with every switch on and the clock at that moment. Nothing is sent yet,
     * because only the planner ran and not the dispatcher.
     */
    private void queueOneOfEach() {
        allOn(NEW_YORK, "18:00");
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        clock.set("2026-10-01T12:00:00Z");
        weight("82", clock.instant());
        goal("WEIGHT", "78", "ACTIVE");

        clock.set("2026-10-07T21:00:00Z");
        weight("77.5", clock.instant());
        recap("2026-09-28", "2026-10-04", 3, 135, 0, 3);
        planner.planDue();

        assertThat(emailRepository.findAll()).extracting(EmailEntity::getStatus)
                .hasSize(3).containsOnly(EmailStatus.PENDING);
        assertThat(sender.sent()).isEmpty();
    }
}
