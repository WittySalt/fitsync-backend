package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.email.OutgoingEmail;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.support.NotificationTestBase;

/** An email that is due goes out once the queueing transaction commits, never twice, and never early. */
class ImmediateDispatchTests extends NotificationTestBase {

    private static final Instant REMOVAL = Instant.parse("2026-11-06T03:00:00Z");

    @Autowired
    private TrainingReminderPlanner planner;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void user() {
        register("ada@example.com", "Ada");
        allOn(NEW_YORK, "18:00");
        clock.set("2026-10-07T12:00:00Z");
    }

    @Test
    void deletionNoticeIsSentAsSoonAsItIsQueuedWithoutATick() {
        requestDeletion(clock.instant(), REMOVAL);

        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.DELETION_SCHEDULED);
        EmailEntity email = onlyEmail(EmailKind.DELETION_SCHEDULED);
        assertThat(email.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(email.getSentAt()).isEqualTo(clock.instant());
        assertThat(email.getAttempts()).isZero();
    }

    @Test
    void nothingIsSentBeforeTheQueueingTransactionCommits() {
        transactions.executeWithoutResult(status -> {
            requestDeletion(clock.instant(), REMOVAL);
            assertThat(sender.sent()).isEmpty();
        });
        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    void aRolledBackQueueingSendsNothing() {
        transactions.executeWithoutResult(status -> {
            requestDeletion(clock.instant(), REMOVAL);
            status.setRollbackOnly();
        });
        assertThat(sender.sent()).isEmpty();
        assertThat(emails(EmailKind.DELETION_SCHEDULED)).isEmpty();
    }

    @Test
    void aLaterTickDoesNotSendItAgain() {
        requestDeletion(clock.instant(), REMOVAL);
        tick.run();
        clock.advance(Duration.ofMinutes(5));
        tick.run();

        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    void aTickRunningDuringTheImmediateSendWaitsAndDoesNotSendItAgain() throws Exception {
        AtomicReference<Thread> tickThread = new AtomicReference<>();
        CountDownLatch tickStarted = new CountDownLatch(1);
        sender.onSend(message -> {
            if (tickThread.get() != null) {
                return;
            }
            // The immediate send holds the row lock here. The tick finds the row still pending and
            // has to wait for that lock before it can look at the row.
            Thread thread = new Thread(() -> {
                tickStarted.countDown();
                tick.run();
            });
            tickThread.set(thread);
            thread.start();
            awaitQuietly(tickStarted);
            pause(Duration.ofMillis(300));
        });

        requestDeletion(clock.instant(), REMOVAL);
        tickThread.get().join(TimeUnit.SECONDS.toMillis(10));

        assertThat(tickThread.get().isAlive()).isFalse();
        assertThat(sender.sent()).hasSize(1);
        assertThat(statusOf(EmailKind.DELETION_SCHEDULED)).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void aFailedImmediateSendStaysPendingForTheTickToRetry() {
        sender.failing(true);
        requestDeletion(clock.instant(), REMOVAL);

        EmailEntity email = onlyEmail(EmailKind.DELETION_SCHEDULED);
        assertThat(email.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(email.getAttempts()).isEqualTo(1);
        assertThat(email.getDueAt()).isEqualTo(Instant.parse("2026-10-07T12:01:00Z"));
        assertThat(sender.sent()).isEmpty();

        sender.failing(false);
        clock.advance(Duration.ofMinutes(1));
        tick.run();
        assertThat(sender.sent()).hasSize(1);
        assertThat(statusOf(EmailKind.DELETION_SCHEDULED)).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void aWeeklyReportForLaterIsNotSentUntilItsTime() {
        // Sunday 4 October 2026 in New York; the report is due Monday 08:00 there (12:00 UTC).
        clock.set("2026-10-04T23:00:00Z");
        recap("2026-09-28", "2026-10-04", 3, 135, 0, 3);

        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.WEEKLY_REPORT)).isEqualTo(EmailStatus.PENDING);

        clock.set("2026-10-05T12:00:00Z");
        tick.run();
        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.WEEKLY_REPORT);
    }

    @Test
    void aTrainingReminderKeepsItsTimeAndIsNotSentEarly() {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        // 16:00 in New York, an hour before the 17:00 reminder for an 18:00 session.
        clock.set("2026-10-07T20:00:00Z");
        planner.planDue();
        tick.run();
        assertThat(sender.sent()).isEmpty();

        clock.set("2026-10-07T21:00:00Z");
        tick.run();
        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.TRAINING_TODAY);
    }

    @Test
    void aDueEmailDuringQuietHoursIsHeldInsteadOfSent() {
        // 00:00 to 23:59 in New York covers the whole moment of the test; E6 ignores it, E4 does not.
        preferences(true, "00:00", "23:59", NEW_YORK, null, "EMAIL");
        clock.set("2026-10-01T12:00:00Z");
        weight("82", clock.instant());
        goal("WEIGHT", "78", "ACTIVE");
        clock.set("2026-10-07T12:00:00Z");
        weight("77.5", clock.instant());

        assertThat(sender.sent()).isEmpty();
        EmailEntity email = onlyEmail(EmailKind.GOAL_REACHED);
        assertThat(email.getStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(email.getDueAt()).isAfter(clock.instant());

        requestDeletion(clock.instant(), REMOVAL);
        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.DELETION_SCHEDULED);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
