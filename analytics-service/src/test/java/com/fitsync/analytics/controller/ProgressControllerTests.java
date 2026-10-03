package com.fitsync.analytics.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Map;
import java.util.UUID;

import com.fitsync.analytics.dto.ProgressFoodResponse;
import com.fitsync.analytics.dto.ProgressGoalResponse;
import com.fitsync.analytics.dto.ProgressHighlight;
import com.fitsync.analytics.dto.ProgressPeriod;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.security.JwtService;
import com.fitsync.analytics.service.ProgressFoodService;
import com.fitsync.analytics.service.ProgressGoalService;
import com.fitsync.analytics.service.ProgressWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProgressController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class ProgressControllerTests {

    private static final LocalDate FROM = LocalDate.parse("2026-09-07");
    private static final LocalDate TO = LocalDate.parse("2026-10-04");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProgressGoalService progressGoalService;

    @MockBean
    private ProgressFoodService progressFoodService;

    @MockBean
    private JwtService jwtService;

    @Test
    void bothPagesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/progress/goal").param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/progress/food").param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(progressGoalService, progressFoodService);
    }

    @Test
    void goalUsesTheCallerFromTheTokenAndTheZoneHeader() throws Exception {
        CurrentUserPrincipal principal = principal();
        ProgressGoalResponse response = new ProgressGoalResponse(new ProgressPeriod(FROM, TO),
                new ProgressPeriod(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-06")),
                new ProgressGoalResponse.Goal("WEIGHT", new BigDecimal("68.0"), LocalDate.parse("2026-12-11")),
                new ProgressGoalResponse.Hero(new BigDecimal("71.2"), LocalDate.parse("2026-10-01"),
                        new BigDecimal("-1.60"), new ProgressGoalResponse.Compared<>(new BigDecimal("-0.41"),
                        new BigDecimal("-0.28")), new ProgressGoalResponse.Compared<>(LocalDate.parse("2026-11-25"),
                        LocalDate.parse("2027-01-10")), 69),
                List.of(new ProgressHighlight("RATE_IN_RANGE", "You lose 0.41 kg a week.",
                        List.of(new ProgressHighlight.Point("Your rate", 0.41)), "kg a week", null,
                        Map.of("weeklyRateKg", new BigDecimal("-0.41")))),
                new ProgressGoalResponse.Weight(List.of(), List.of(), List.of(), null,
                        "28-day average change carried forward"),
                null, new ProgressGoalResponse.Pace(List.of()), List.of(),
                new ProgressGoalResponse.Road("LOSE", "AHEAD", new BigDecimal("74.6"), LocalDate.parse("2026-07-06"),
                        new BigDecimal("71.2"), LocalDate.parse("2026-10-01"), new BigDecimal("68.0"),
                        LocalDate.parse("2026-12-11"), new BigDecimal("3.2"), new BigDecimal("0.52"),
                        new BigDecimal("0.55"), LocalDate.parse("2026-11-25"),
                        List.of(new ProgressGoalResponse.Tick(74, LocalDate.parse("2026-07-30"), null),
                                new ProgressGoalResponse.Tick(71, null, LocalDate.parse("2026-10-05"))), null),
                "WEIGHT");
        when(progressGoalService.getGoal(eq(principal.userId()), any(), eq("Asia/Singapore"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/progress/goal")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07").param("to", "2026-10-04")
                        .param("userId", UUID.randomUUID().toString())
                        .header("X-Timezone", "Asia/Singapore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previous.from").value("2026-08-10"))
                .andExpect(jsonPath("$.goal.targetKg").value(68.0))
                .andExpect(jsonPath("$.hero.weeklyRate.value").value(-0.41))
                .andExpect(jsonPath("$.hero.projectedDate.previous").value("2027-01-10"))
                .andExpect(jsonPath("$.highlights[0].code").value("RATE_IN_RANGE"))
                .andExpect(jsonPath("$.highlights[0].inputs.weeklyRateKg").value(-0.41))
                .andExpect(jsonPath("$.weight.method").value("28-day average change carried forward"))
                .andExpect(jsonPath("$.leadType").value("WEIGHT"))
                .andExpect(jsonPath("$.road.direction").value("LOSE"))
                .andExpect(jsonPath("$.road.state").value("AHEAD"))
                .andExpect(jsonPath("$.road.distanceKg").value(3.2))
                .andExpect(jsonPath("$.road.fractionWeight").value(0.52))
                .andExpect(jsonPath("$.road.projectedDate").value("2026-11-25"))
                .andExpect(jsonPath("$.road.ticks[0].crossedOn").value("2026-07-30"))
                .andExpect(jsonPath("$.road.ticks[1].projectedOn").value("2026-10-05"))
                .andExpect(jsonPath("$.road.keep").value(nullValue()));
        verify(progressGoalService).getGoal(principal.userId(), ProgressWindow.of(FROM, TO), "Asia/Singapore");
    }

    @Test
    void foodPassesNoZoneWithoutAHeader() throws Exception {
        CurrentUserPrincipal principal = principal();
        ProgressFoodResponse response = new ProgressFoodResponse(new ProgressPeriod(FROM, TO),
                new ProgressPeriod(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-06")),
                new ProgressFoodResponse.Hero(new ProgressFoodResponse.Compared<>(2123, 2190),
                        new ProgressFoodResponse.Compared<>(new BigDecimal("136.0"), new BigDecimal("150.0")),
                        new ProgressFoodResponse.Balance(-279, 4), new ProgressFoodResponse.DaysLogged(26, 24, 30)),
                List.of(), null,
                new ProgressFoodResponse.Macros(null, null, null, List.of()),
                new ProgressFoodResponse.Plate("CALORIES", List.of(), List.of(), List.of()),
                new ProgressFoodResponse.Meals(List.of(new ProgressFoodResponse.MealShare("DINNER", 38, 2, 807))),
                new ProgressFoodResponse.Habits(26));
        when(progressFoodService.getFood(eq(principal.userId()), any(), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/progress/food")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-09-07").param("to", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hero.eatenPerDay.value").value(2123))
                .andExpect(jsonPath("$.hero.daysLogged.days").value(30))
                .andExpect(jsonPath("$.plate.basis").value("CALORIES"))
                .andExpect(jsonPath("$.meals.share[0].sharePct").value(38));
    }

    @Test
    void rejectsFromAfterToPeriodsOver370DaysAndMissingOrMalformedDates() throws Exception {
        for (String path : List.of("/api/v1/analytics/progress/goal", "/api/v1/analytics/progress/food")) {
            mockMvc.perform(get(path).with(authentication(authToken(principal())))
                            .param("from", "2026-10-05").param("to", "2026-10-04"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("from must not be after to"));
            mockMvc.perform(get(path).with(authentication(authToken(principal())))
                            .param("from", "2025-09-28").param("to", "2026-10-03"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("The period must not exceed 370 days"));
            mockMvc.perform(get(path).with(authentication(authToken(principal()))).param("to", "2026-10-03"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(path).with(authentication(authToken(principal())))
                            .param("from", "yesterday").param("to", "2026-10-03"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(progressGoalService, progressFoodService);
    }

    private UsernamePasswordAuthenticationToken authToken(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }

    private CurrentUserPrincipal principal() {
        return new CurrentUserPrincipal(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice@example.com", "Alice", "USER");
    }
}
