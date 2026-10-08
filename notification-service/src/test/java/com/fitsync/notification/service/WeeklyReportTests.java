package com.fitsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.support.NotificationTestBase;

/** E5 for the week of Monday 28 September to Sunday 4 October 2026. */
class WeeklyReportTests extends NotificationTestBase {

    @BeforeEach
    void user() {
        register("ada@example.com", "Ada");
        clock.set("2026-10-05T01:00:00Z");
    }

    @Test
    void queuedForMondayEightInTheUsersTimezoneWithTheWeeksFacts() {
        allOn(NEW_YORK, null);
        goal("WORKOUT_FREQUENCY", "4", "ACTIVE");
        weight("80.0", Instant.parse("2026-09-27T12:00:00Z"));
        weight("79.6", Instant.parse("2026-10-01T12:00:00Z"));
        weight("79.4", Instant.parse("2026-10-04T12:00:00Z"));

        recap("2026-09-28", "2026-10-04", 3, 135, 12400, 4);

        EmailEntity record = onlyEmail(EmailKind.WEEKLY_REPORT);
        assertThat(record.getDueAt()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
        assertThat(record.getExpiresAt()).isEqualTo(Instant.parse("2026-10-06T12:00:00Z"));
        assertThat(record.getDedupeKey()).isEqualTo("2026-09-28");

        clock.set("2026-10-05T11:55:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();

        clock.set("2026-10-05T12:00:00Z");
        tick.run();
        assertThat(sender.sent()).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("Your week, 28 Sep to 4 Oct");
            assertThat(email.bodyText())
                    .contains("Workouts: 3 (weekly goal: 4)")
                    .contains("Training time: 135 min")
                    .contains("Active days: 4 of 7")
                    .contains("Calories logged: 12,400 kcal in total")
                    .contains("Weight: 80 kg to 79.4 kg (-0.6 kg)")
                    .contains("See your progress:\nhttps://app.fitsync.test/progress")
                    .contains("You get this because the Weekly progress report is on.")
                    .doesNotContainIgnoringCase("average");
        });
    }

    @Test
    void linesWithoutDataAreLeftOut() {
        allOn(NEW_YORK, null);
        weight("79.4", Instant.parse("2026-10-04T12:00:00Z"));

        recap("2026-09-28", "2026-10-04", 2, 60, 0, 2);
        clock.set("2026-10-05T12:00:00Z");
        tick.run();

        assertThat(sender.sent()).singleElement().satisfies(email -> assertThat(email.bodyText())
                .contains("Workouts: 2\n")
                .doesNotContain("weekly goal")
                .doesNotContain("Calories")
                .doesNotContain("Weight:"));
    }

    @Test
    void aLateRecapIsDueAtOnce() {
        allOn(NEW_YORK, null);
        clock.set("2026-10-05T15:00:00Z");
        recap("2026-09-28", "2026-10-04", 1, 30, 0, 1);

        assertThat(onlyEmail(EmailKind.WEEKLY_REPORT).getDueAt()).isEqualTo(Instant.parse("2026-10-05T15:00:00Z"));
        tick.run();
        assertThat(sender.sent()).hasSize(1);
    }

    @Test
    void nothingForAWeekWithoutActivity() {
        allOn(NEW_YORK, null);
        recap("2026-09-28", "2026-10-04", 0, 0, 0, 0);
        assertThat(emails(EmailKind.WEEKLY_REPORT)).isEmpty();
    }

    @Test
    void droppedWhenTheChannelIsNotEmail() {
        preferences(true, null, null, NEW_YORK, null, "PUSH");
        recap("2026-09-28", "2026-10-04", 3, 135, 0, 3);

        clock.set("2026-10-05T12:00:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.WEEKLY_REPORT)).isEqualTo(EmailStatus.DROPPED);
    }

    @Test
    void droppedOnceExpired() {
        allOn(NEW_YORK, null);
        recap("2026-09-28", "2026-10-04", 3, 135, 0, 3);

        clock.set("2026-10-06T12:00:00Z");
        tick.run();
        assertThat(sender.sent()).isEmpty();
        assertThat(statusOf(EmailKind.WEEKLY_REPORT)).isEqualTo(EmailStatus.DROPPED);
    }
}
