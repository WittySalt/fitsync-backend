package com.fitsync.recommendation.coach.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.reading.ReadingFact;
import com.fitsync.recommendation.coach.reading.WeeklyReviewService;
import com.fitsync.recommendation.coach.reading.WeeklyReviewStatus;
import com.fitsync.recommendation.coach.reading.WeeklyReviewView;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CoachReviewController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class CoachReviewControllerTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WeeklyReviewService reviewService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("reviews need a signed-in user")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/coach/reviews").header("Authorization", "Bearer caller"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("the caller's own reviews come back newest first, eight weeks by default, in the header's zone")
    void returnsTheCallersReviews() throws Exception {
        ReadingFact sessions = new ReadingFact(1, "SESSIONS", "Sessions", "3", "sessions", BigDecimal.valueOf(3),
                "Up 1", "2 the week before", "TRAINING");
        when(reviewService.reviews(eq(USER_ID), anyString(), eq("Asia/Singapore"), eq(8))).thenReturn(List.of(
                new WeeklyReviewView(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"), true,
                        WeeklyReviewStatus.READY, "Your week so far.", "You trained 3 sessions [1].",
                        List.of(1), "RULES", List.of(sessions)),
                new WeeklyReviewView(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"), false,
                        WeeklyReviewStatus.NONE, null, null, List.of(), null, List.of())));

        mockMvc.perform(get("/api/v1/coach/reviews")
                        .header("Authorization", "Bearer caller")
                        .header("X-Timezone", "Asia/Singapore")
                        .with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weekStart").value("2026-09-28"))
                .andExpect(jsonPath("$[0].weekEnd").value("2026-10-04"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].headline").value("Your week so far."))
                .andExpect(jsonPath("$[0].facts[0].card").value("TRAINING"))
                .andExpect(jsonPath("$[0].citations[0]").value(1))
                .andExpect(jsonPath("$[1].status").value("NONE"));
        verify(reviewService).reviews(eq(USER_ID), eq("caller"), eq("Asia/Singapore"), eq(8));
    }

    @Test
    @DisplayName("with no zone header the service falls back to the stored zone")
    void noZoneHeader() throws Exception {
        when(reviewService.reviews(eq(USER_ID), anyString(), isNull(), eq(4))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/coach/reviews?weeks=4")
                        .header("Authorization", "Bearer caller")
                        .with(authentication(principal())))
                .andExpect(status().isOk());
        verify(reviewService).reviews(eq(USER_ID), anyString(), isNull(), eq(4));
    }

    @Test
    @DisplayName("weeks outside 1 to 26 is a 400")
    void weeksOutOfRange() throws Exception {
        for (String weeks : List.of("0", "27", "-1", "many")) {
            mockMvc.perform(get("/api/v1/coach/reviews?weeks=" + weeks)
                            .header("Authorization", "Bearer caller")
                            .with(authentication(principal())))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reviewService);
    }

    private static UsernamePasswordAuthenticationToken principal() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(USER_ID, "user@example.com", "User", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
