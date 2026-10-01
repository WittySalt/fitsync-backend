package com.fitsync.analytics.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.config.SecurityConfig;
import com.fitsync.analytics.security.JwtAuthenticationFilter;
import com.fitsync.analytics.dto.AnalyticsRebuildResponse;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.security.JwtService;
import com.fitsync.analytics.service.AnalyticsRebuildService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalAnalyticsController.class)
@Import({
        com.fitsync.web.config.CommonWebErrorConfiguration.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class
})
@ActiveProfiles("test")
class InternalAnalyticsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsRebuildService analyticsRebuildService;

    @MockBean
    private JwtService jwtService;

    @Test
    void shouldRejectNonAdminInternalRebuildRequest() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/dashboard/rebuild",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .with(csrf())
                        .with(authentication(authToken(userPrincipal()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToRebuildDashboard() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(analyticsRebuildService.rebuildDashboard(eq(userId)))
                .thenReturn(new AnalyticsRebuildResponse(userId, "dashboard", null, Instant.parse("2026-06-14T08:00:00Z")));

        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/dashboard/rebuild", userId)
                        .with(csrf())
                        .with(authentication(authToken(adminPrincipal()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("dashboard"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void shouldAllowAdminToRebuildNutritionForDate() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(analyticsRebuildService.rebuildNutrition(eq(userId), eq(LocalDate.parse("2026-06-14"))))
                .thenReturn(new AnalyticsRebuildResponse(userId, "nutrition", LocalDate.parse("2026-06-14"),
                        Instant.parse("2026-06-14T08:00:00Z")));

        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/nutrition/rebuild", userId)
                        .with(csrf())
                        .with(authentication(authToken(adminPrincipal())))
                        .param("date", "2026-06-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("nutrition"))
                .andExpect(jsonPath("$.date").value("2026-06-14"));
    }

    @Test
    void shouldRejectUnauthenticatedDashboardRebuild() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/dashboard/rebuild",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNonAdminNutritionRebuild() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/nutrition/rebuild",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .with(csrf())
                        .with(authentication(authToken(userPrincipal())))
                        .param("date", "2026-06-14"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnauthenticatedNutritionRebuild() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/nutrition/rebuild",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .with(csrf())
                        .param("date", "2026-06-14"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNonAdminWorkoutRebuild() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/workouts/rebuild",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .with(csrf())
                        .with(authentication(authToken(userPrincipal())))
                        .param("date", "2026-06-14"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnauthenticatedWorkoutRebuild() throws Exception {
        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/workouts/rebuild",
                        UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .with(csrf())
                        .param("date", "2026-06-14"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAdminToRebuildWorkoutsForDate() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(analyticsRebuildService.rebuildWorkouts(eq(userId), eq(LocalDate.parse("2026-06-14"))))
                .thenReturn(new AnalyticsRebuildResponse(userId, "workouts", LocalDate.parse("2026-06-14"),
                        Instant.parse("2026-06-14T08:00:00Z")));

        mockMvc.perform(post("/api/v1/internal/analytics/users/{userId}/workouts/rebuild", userId)
                        .with(csrf())
                        .with(authentication(authToken(adminPrincipal())))
                        .param("date", "2026-06-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("workouts"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.date").value("2026-06-14"));
    }

    private UsernamePasswordAuthenticationToken authToken(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }

    private CurrentUserPrincipal userPrincipal() {
        return new CurrentUserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice@example.com",
                "Alice",
                "USER");
    }

    private CurrentUserPrincipal adminPrincipal() {
        return new CurrentUserPrincipal(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "admin@example.com",
                "Admin",
                "ADMIN");
    }
}
