package com.fitsync.recommendation.coach.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.fitsync.recommendation.coach.dto.CoachQualityAnswersView;
import com.fitsync.recommendation.coach.dto.CoachQualityDailyView;
import com.fitsync.recommendation.coach.dto.CoachQualityLatencyView;
import com.fitsync.recommendation.coach.dto.CoachQualityResponse;
import com.fitsync.recommendation.coach.service.CoachQualityService;
import com.fitsync.recommendation.config.SecurityConfig;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.security.JwtAuthenticationFilter;
import com.fitsync.recommendation.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security boundary and input validation for the admin coach-quality endpoint. What the numbers
 * mean is proven in {@code CoachQualityServiceTests}; this class only checks who may call it and how
 * bad input is rejected.
 */
@WebMvcTest(controllers = CoachQualityController.class)
@Import({
        com.fitsync.web.config.CommonWebErrorConfiguration.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
@ActiveProfiles("test")
class CoachQualityControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoachQualityService qualityService;

    @MockBean
    private JwtService jwtService;

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/internal/coach/quality"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .with(authentication(authToken("USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGetsTheDefaultsAppliedByTheService() throws Exception {
        when(qualityService.quality(null, null, null)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .with(authentication(authToken("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-24"))
                .andExpect(jsonPath("$.to").value("2026-09-30"))
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.answers.total").value(1));
    }

    @Test
    void adminCanPassExplicitDatesAndTimezone() throws Exception {
        when(qualityService.quality(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"),
                "Asia/Singapore")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-07")
                        .header("X-Timezone", "Asia/Singapore")
                        .with(authentication(authToken("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void fromAfterToIsRejectedByTheService() throws Exception {
        when(qualityService.quality(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-01"), null))
                .thenThrow(new com.fitsync.web.error.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "COACH_QUALITY_INVALID_PARAMS",
                        "The date range or timezone for the coach quality report is invalid."));

        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .param("from", "2026-09-10")
                        .param("to", "2026-09-01")
                        .with(authentication(authToken("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRangeOver92DaysIsRejectedByTheService() throws Exception {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = from.plusDays(92);
        when(qualityService.quality(from, to, null))
                .thenThrow(new com.fitsync.web.error.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "COACH_QUALITY_INVALID_PARAMS",
                        "The date range or timezone for the coach quality report is invalid."));

        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(authentication(authToken("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownTimezoneHeaderIsIgnored() throws Exception {
        when(qualityService.quality(null, null, null)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .header("X-Timezone", "Mars/Base")
                        .with(authentication(authToken("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void anUnparseableDateIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/internal/coach/quality")
                        .param("from", "not-a-date")
                        .with(authentication(authToken("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    private static CoachQualityResponse sampleResponse() {
        return new CoachQualityResponse(
                "2026-09-24", "2026-09-30", "Asia/Singapore",
                new CoachQualityAnswersView(1, 1, 0, 0, 0, 0, List.of(), List.of()),
                new CoachQualityLatencyView(1, 2100, 2100),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new CoachQualityDailyView("2026-09-30", 1, 1, 0, 0, 0)));
    }

    private static UsernamePasswordAuthenticationToken authToken(String role) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "admin@example.com", "Admin", role);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + role));
    }
}
