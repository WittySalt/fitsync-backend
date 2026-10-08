package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.email.OutgoingEmail;
import com.fitsync.notification.support.NotificationTestBase;

/** E4. */
class GoalReachedTests extends NotificationTestBase {

    @BeforeEach
    void user() {
        register("ada@example.com", "Ada");
        allOn(NEW_YORK, null);
        clock.set("2026-09-01T12:00:00Z");
    }

    @Test
    void losingGoalIsReachedAtOrBelowTheTarget() {
        weight("82.0", clock.instant());
        UUID goalId = goal("WEIGHT", "78", "ACTIVE");

        clock.set("2026-10-01T12:00:00Z");
        weight("78.5", clock.instant());
        tick.run();
        assertThat(sender.sent()).isEmpty();

        clock.set("2026-10-07T12:00:00Z");
        weight("78.0", clock.instant());
        tick.run();

        OutgoingEmail email = sender.sent().get(0);
        assertThat(sender.sent()).hasSize(1);
        assertThat(email.subject()).isEqualTo("Weight goal reached: 78 kg");
        assertThat(email.bodyText())
                .contains("Start: 82 kg")
                .contains("Now: 78 kg")
                .contains("Days since the goal was set: 36")
                .contains("Set your next goal:\nhttps://app.fitsync.test/me")
                .contains("You get this because Goal progress reminders are on.");
        assertThat(onlyEmail(EmailKind.GOAL_REACHED).getDedupeKey()).isEqualTo(goalId.toString());
    }

    @Test
    void gainingGoalIsReachedAtOrAboveTheTarget() {
        weight("60.0", clock.instant());
        goal("WEIGHT", "64", "ACTIVE");

        clock.set("2026-09-20T12:00:00Z");
        weight("63.9", clock.instant());
        assertThat(emails(EmailKind.GOAL_REACHED)).isEmpty();

        weight("64.3", clock.instant().plusSeconds(60));
        tick.run();
        assertThat(sender.sent()).singleElement()
                .satisfies(email -> assertThat(email.bodyText()).contains("Start: 60 kg").contains("Now: 64.3 kg"));
    }

    @Test
    void sentOnlyOncePerGoal() {
        weight("82.0", clock.instant());
        goal("WEIGHT", "78", "ACTIVE");

        weight("77.8", clock.instant().plusSeconds(60));
        tick.run();
        clock.advance(Duration.ofDays(1));
        weight("77.5", clock.instant());
        tick.run();

        assertThat(sender.sent()).hasSize(1);
        assertThat(emails(EmailKind.GOAL_REACHED)).hasSize(1);
    }

    @Test
    void nothingWhenTheStartWeightIsUnknown() {
        UUID noStart = goal("WEIGHT", "78", "ACTIVE");
        // The first weight becomes the start, so it cannot also be the arrival.
        weight("77.0", clock.instant());
        assertThat(goalRepository.findById(noStart).orElseThrow().getStartWeightKg()).isEqualByComparingTo("77");
        assertThat(emails(EmailKind.GOAL_REACHED)).isEmpty();
    }

    @Test
    void otherGoalTypesAndInactiveGoalsNeverCount() {
        weight("82.0", clock.instant());
        goal("CALORIES", "2000", "ACTIVE");
        goal("WEIGHT", "78", "ARCHIVED");

        weight("70.0", clock.instant().plusSeconds(60));
        assertThat(emails(EmailKind.GOAL_REACHED)).isEmpty();
    }

    @Test
    void queuedEvenWhenTheToggleIsOffButDroppedWhenItsTimeComes() {
        preferencesUpdated.consume(event("NotificationPreferencesUpdated",
                new com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload(true, true, false, true,
                        "EMAIL", null, null, NEW_YORK, null)));
        weight("82.0", clock.instant());
        goal("WEIGHT", "78", "ACTIVE");
        weight("77.0", Instant.parse("2026-09-02T12:00:00Z"));
        tick.run();

        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.GOAL_REACHED))
                .isEqualTo(com.fitsync.notification.domain.EmailStatus.DROPPED);
    }
}
