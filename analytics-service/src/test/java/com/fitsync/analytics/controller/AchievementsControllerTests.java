package com.fitsync.analytics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsFootprintResponse;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.security.JwtService;
import com.fitsync.analytics.service.AchievementsService;
import com.fitsync.analytics.service.FootprintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AchievementsController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class AchievementsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AchievementsService achievementsService;

    @MockBean
    private FootprintService footprintService;

    @MockBean
    private JwtService jwtService;

    @Test
    void bothEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/achievements")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/me/footprint")).andExpect(status().isUnauthorized());
    }

    @Test
    void achievementsAreReturnedForTheCaller() throws Exception {
        AnalyticsAchievementsResponse.Badge earned = new AnalyticsAchievementsResponse.Badge("FULL_WEEK", "SHOWING_UP",
                "A full week", "Reach your sessions a week target in one Monday to Sunday week.", "EARNED",
                LocalDate.parse("2026-10-01"),
                new AnalyticsAchievementsResponse.Progress(BigDecimal.valueOf(4), BigDecimal.valueOf(4), "sessions"),
                new AnalyticsAchievementsResponse.Evidence(LocalDate.parse("2026-09-28"), null, null, null,
                        List.of(true, false, true, true, true, false, false)));
        AnalyticsAchievementsResponse.Badge next = new AnalyticsAchievementsResponse.Badge("WORKOUTS_50",
                "SHOWING_UP", "50 workouts", "Finish 50 sessions.", "NEXT", null,
                new AnalyticsAchievementsResponse.Progress(BigDecimal.valueOf(31), BigDecimal.valueOf(50), "sessions"),
                null);
        when(achievementsService.getAchievements(any())).thenReturn(new AnalyticsAchievementsResponse(7, 17,
                List.of(new AnalyticsAchievementsResponse.Theme("SHOWING_UP", "Showing up", List.of(
                        new AnalyticsAchievementsResponse.Path("WEEKS", List.of(earned))))),
                earned, List.of(next), List.of(),
                new AnalyticsAchievementsResponse.Pace(new BigDecimal("3.8"), 4)));

        mockMvc.perform(get("/api/v1/analytics/achievements").with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earned").value(7))
                .andExpect(jsonPath("$.total").value(17))
                .andExpect(jsonPath("$.themes[0].paths[0].nodes[0].code").value("FULL_WEEK"))
                .andExpect(jsonPath("$.latest.earnedOn").value("2026-10-01"))
                .andExpect(jsonPath("$.latest.evidence.weekStart").value("2026-09-28"))
                .andExpect(jsonPath("$.latest.evidence.weekDays[0]").value(true))
                .andExpect(jsonPath("$.nextUp[0].progress.current").value(31))
                .andExpect(jsonPath("$.nextUp[0].progress.target").value(50))
                .andExpect(jsonPath("$.pace.sessionsPerWeek").value(3.8));
    }

    @Test
    void theFootprintPassesTheOptionalSinceDate() throws Exception {
        when(footprintService.getFootprint(any(), any())).thenReturn(new AnalyticsFootprintResponse(
                LocalDate.parse("2026-08-04"), LocalDate.parse("2026-08-03"), LocalDate.parse("2026-08-09"),
                List.of(new AnalyticsFootprintResponse.Day(LocalDate.parse("2026-08-04"), true, false)),
                new AnalyticsFootprintResponse.Totals(31, 58, 7)));

        mockMvc.perform(get("/api/v1/analytics/me/footprint").param("since", "2026-08-04")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-03"))
                .andExpect(jsonPath("$.days[0].trained").value(true))
                .andExpect(jsonPath("$.totals.daysLogged").value(58));
        verify(footprintService).getFootprint(any(), eq(LocalDate.parse("2026-08-04")));

        mockMvc.perform(get("/api/v1/analytics/me/footprint").with(authentication(authToken())))
                .andExpect(status().isOk());
        verify(footprintService).getFootprint(any(), isNull());
    }

    @Test
    void aMalformedSinceIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/me/footprint").param("since", "4 Aug")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest());
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "alice@example.com", "Alice", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
