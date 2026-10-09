package com.fitsync.notification.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.domain.EmailTrigger;
import com.fitsync.notification.email.OutgoingEmail;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.service.TrainingReminderPlanner;
import com.fitsync.notification.support.NotificationTestBase;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * The admin console's "send now": who may call it, which kinds it offers and why not, that it never
 * invents content, that the opt-in holds while quiet hours and the daily key do not, the row's
 * audit fields, the hourly limit, and that the answer carries no address.
 */
@AutoConfigureMockMvc
class AdminSendNowTests extends NotificationTestBase {

    private static final String SECRET = "change-me-change-me-change-me-change-me";
    private static final String PATH = "/api/v1/internal/users/{userId}/emails";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrainingReminderPlanner planner;

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void user() {
        register("ada@example.com", "Ada");
        allOn(NEW_YORK, "18:00");
        // Wednesday 7 October 2026, 08:00 in New York.
        clock.set("2026-10-07T12:00:00Z");
    }

    // --- Security -------------------------------------------------------------------------------

    @Test
    void withoutATokenBothEndpointsAnswer401() throws Exception {
        mockMvc.perform(get(PATH, userId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH, userId).contentType(MediaType.APPLICATION_JSON).content(body("GOAL_REACHED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOrdinaryUserGets403AndNothingIsSent() throws Exception {
        String user = bearer(userId, "USER");
        mockMvc.perform(get(PATH, userId).header(HttpHeaders.AUTHORIZATION, user)).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH, userId).header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body("GOAL_REACHED")))
                .andExpect(status().isForbidden());
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void aForgedTokenIsTreatedAsNoToken() throws Exception {
        String forged = "Bearer " + Jwts.builder().claim("userId", adminId.toString()).claim("role", "ADMIN")
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(
                        StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get(PATH, userId).header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().isUnauthorized());
    }

    // --- Kinds ----------------------------------------------------------------------------------

    @Test
    void theDeletionNoticeCannotBeAskedFor() throws Exception {
        sendAs(body("DELETION_SCHEDULED")).andExpect(status().isBadRequest());
        sendAs(body("ACCOUNT_DELETION")).andExpect(status().isBadRequest());
        sendAs("{}").andExpect(status().isBadRequest());
        assertThat(emailRepository.findAll()).isEmpty();
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void anUnknownUserIs404() throws Exception {
        mockMvc.perform(get(PATH, UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void availabilityListsTheThreeKindsWithTheReasonForEachOneThatCannotGo() throws Exception {
        mockMvc.perform(get(PATH, userId).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kinds.length()").value(3))
                .andExpect(jsonPath("$.kinds[0].kind").value("TRAINING_REMINDER"))
                .andExpect(jsonPath("$.kinds[0].available").value(false))
                .andExpect(jsonPath("$.kinds[0].reason").value("NO_SESSION_TODAY"))
                .andExpect(jsonPath("$.kinds[0].message").value("This user's plan has no session today"))
                .andExpect(jsonPath("$.kinds[1].kind").value("GOAL_REACHED"))
                .andExpect(jsonPath("$.kinds[1].reason").value("NO_WEIGHT_GOAL"))
                .andExpect(jsonPath("$.kinds[2].kind").value("WEEKLY_REPORT"))
                .andExpect(jsonPath("$.kinds[2].reason").value("NO_RECAP"));

        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        mockMvc.perform(get(PATH, userId).header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(jsonPath("$.kinds[0].available").value(true))
                .andExpect(jsonPath("$.kinds[0].reason").doesNotExist());
    }

    // --- Opt-in ---------------------------------------------------------------------------------

    @Test
    void theKindToggleStillApplies() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        preferencesUpdated.consume(event("NotificationPreferencesUpdated",
                new com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload(true, false, true, true,
                        "EMAIL", null, null, NEW_YORK, "18:00")));

        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKOUT_REMINDERS_OFF"))
                .andExpect(jsonPath("$.message").value("This user has Workout reminders off"));
        assertThat(emails(EmailKind.TRAINING_TODAY)).isEmpty();
    }

    @Test
    void theEmailSwitchStillApplies() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        preferences(false, null, null, NEW_YORK, "18:00", "EMAIL");

        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_OFF"));
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void quietHoursDoNotHoldAnAdminSend() throws Exception {
        preferences(true, "00:00", "23:59", NEW_YORK, "18:00", "EMAIL");
        plan(day(3, "STRENGTH", "Upper body", 45, 6));

        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SENT"));
        assertThat(sender.sent()).extracting(OutgoingEmail::kind).containsExactly(EmailKind.TRAINING_TODAY);
    }

    // --- E1 -------------------------------------------------------------------------------------

    @Test
    void aTrainingReminderIsTodaysSessionAndDoesNotBlockTheUsualOne() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));

        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TRAINING_REMINDER"))
                .andExpect(jsonPath("$.outcome").value("SENT"));
        assertThat(sender.sent()).singleElement()
                .satisfies(email -> assertThat(email.subject()).isEqualTo("Today: Upper body · 45 min at 18:00"));

        // The planned reminder at 17:00 New York time still goes out.
        clock.set("2026-10-07T21:00:00Z");
        planner.planDue();
        tick.run();
        assertThat(sender.sent()).extracting(OutgoingEmail::kind)
                .containsExactly(EmailKind.TRAINING_TODAY, EmailKind.TRAINING_TODAY);
    }

    @Test
    void noSessionTodayRefusesTheTrainingReminder() throws Exception {
        // Thursday only; the test day is a Wednesday.
        plan(day(4, "STRENGTH", "Legs", 45, 6));
        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_SESSION_TODAY"));
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void aWorkoutAlreadyLoggedTodayRefusesTheTrainingReminder() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        workoutAt(clock.instant());
        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKOUT_LOGGED_TODAY"));
    }

    // --- E4 -------------------------------------------------------------------------------------

    @Test
    void aReachedGoalIsSentAgainWithTheUsualWordsAndFooter() throws Exception {
        reachGoal("77.5");
        sender.reset();

        sendAs(body("GOAL_REACHED")).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SENT"));

        assertThat(sender.sent()).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("Weight goal reached: 78 kg");
            assertThat(email.bodyText())
                    .contains("You get this because ")
                    .contains("https://app.fitsync.test/settings?section=notifications")
                    .doesNotContainIgnoringCase("test email")
                    .doesNotContainIgnoringCase("admin");
        });
    }

    @Test
    void aGoalNotReachedIsRefusedAndNothingIsInvented() throws Exception {
        reachGoal("80");

        sendAs(body("GOAL_REACHED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_REACHED"));
        assertThat(emails(EmailKind.GOAL_REACHED)).isEmpty();
        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void aGoalWithoutAnyWeightIsRefused() throws Exception {
        goal("WEIGHT", "78", "ACTIVE");
        sendAs(body("GOAL_REACHED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_WEIGHT"));
    }

    // --- E5 -------------------------------------------------------------------------------------

    @Test
    void lastWeeksReportIsSentFromItsRecap() throws Exception {
        recap("2026-09-28", "2026-10-04", 3, 135, 0, 3);
        String subject = onlyEmail(EmailKind.WEEKLY_REPORT).getSubject();
        sender.reset();

        sendAs(body("WEEKLY_REPORT")).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SENT"));
        assertThat(sender.sent()).singleElement()
                .satisfies(email -> assertThat(email.subject()).isEqualTo(subject));
    }

    @Test
    void withoutARecapForLastWeekTheReportIsRefused() throws Exception {
        // A recap for the week before last is not last week's.
        recap("2026-09-21", "2026-09-27", 3, 135, 0, 3);
        sendAs(body("WEEKLY_REPORT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_RECAP"));
    }

    // --- Record, limit and answer -----------------------------------------------------------------

    @Test
    void theRowRecordsTheAdminAndItsOwnKeyAndTheAnswerHasNoAddress() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));

        String answer = sendAs(body("TRAINING_REMINDER")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(answer).doesNotContain("ada@example.com").doesNotContain("@");
        EmailEntity row = onlyEmail(EmailKind.TRAINING_TODAY);
        assertThat(row.getTrigger()).isEqualTo(EmailTrigger.ADMIN);
        assertThat(row.getAdminId()).isEqualTo(adminId);
        assertThat(row.getDedupeKey()).startsWith("admin:");
        assertThat(row.getStatus()).isEqualTo(EmailStatus.SENT);
    }

    @Test
    void anAdminMaySendTenAnHour() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        for (int i = 0; i < AdminEmailService.HOURLY_LIMIT; i++) {
            sendAs(body("TRAINING_REMINDER")).andExpect(status().isOk());
        }
        sendAs(body("TRAINING_REMINDER"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ADMIN_EMAIL_LIMIT"));
        assertThat(sender.sent()).hasSize(AdminEmailService.HOURLY_LIMIT);

        clock.advance(java.time.Duration.ofMinutes(61));
        sendAs(body("TRAINING_REMINDER")).andExpect(status().isOk());
    }

    @Test
    void anSesFailureIsReportedAndLeftForTheTick() throws Exception {
        plan(day(3, "STRENGTH", "Upper body", 45, 6));
        sender.failing(true);

        sendAs(body("TRAINING_REMINDER")).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("RETRYING"));

        sender.failing(false);
        clock.advance(java.time.Duration.ofMinutes(1));
        tick.run();
        assertThat(sender.sent()).hasSize(1);
        assertThat(statusOf(EmailKind.TRAINING_TODAY)).isEqualTo(EmailStatus.SENT);
    }

    // --- Helpers --------------------------------------------------------------------------------

    /** A weight goal of 78 kg from 82 kg, then the given latest weight. */
    private void reachGoal(String latestKg) {
        clock.set("2026-10-01T12:00:00Z");
        weight("82", clock.instant());
        goal("WEIGHT", "78", "ACTIVE");
        clock.set("2026-10-07T12:00:00Z");
        weight(latestKg, clock.instant());
    }

    private ResultActions sendAs(String json) throws Exception {
        return mockMvc.perform(post(PATH, userId).header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String admin() {
        return bearer(adminId, "ADMIN");
    }

    private static String body(String kind) {
        return "{\"kind\":\"" + kind + "\"}";
    }

    private static String bearer(UUID id, String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder()
                .subject(id + "@example.com")
                .claim("userId", id.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
