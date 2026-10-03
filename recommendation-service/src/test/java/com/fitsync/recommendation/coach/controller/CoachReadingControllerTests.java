package com.fitsync.recommendation.coach.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.reading.CoachReadingResponse;
import com.fitsync.recommendation.coach.reading.CoachReadingService;
import com.fitsync.recommendation.coach.reading.ModelReadingView;
import com.fitsync.recommendation.coach.reading.ReadingStatus;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
import com.fitsync.recommendation.coach.reading.ReadingUnavailableException;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CoachReadingController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class CoachReadingControllerTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoachReadingService readingService;

    @MockBean
    private JwtService jwtService;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/coach/readings/workout")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheReadingShapeForAnAuthenticatedUser() throws Exception {
        when(readingService.workoutReading(eq(USER_ID), any(String.class), eq("Asia/Singapore")))
                .thenReturn(new CoachReadingResponse(
                        ReadingSurface.WORKOUT, ReadingStatus.READY, "9f2c4e1ab03d77aa",
                        "PREVIOUS_WEEK", null, null, List.of(),
                        new ModelReadingView("Your lifts grew while your sessions stayed the same length.",
                                "You moved 1,923 kg a session [1], up from 1,716 kg, in 50 min [2].",
                                List.of(1, 2), "RULES", Instant.parse("2026-09-28T12:00:00Z")),
                        List.of()));

        mockMvc.perform(get("/api/v1/coach/readings/workout")
                        .header("X-Timezone", "Asia/Singapore")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surface").value("WORKOUT"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.readingId").value("9f2c4e1ab03d77aa"))
                .andExpect(jsonPath("$.period").value("PREVIOUS_WEEK"))
                .andExpect(jsonPath("$.reading.source").value("RULES"));
    }

    @Test
    void defaultsTimezoneToUtcWhenNotGiven() throws Exception {
        when(readingService.workoutReading(eq(USER_ID), any(String.class), eq("UTC")))
                .thenReturn(new CoachReadingResponse(
                        ReadingSurface.WORKOUT, ReadingStatus.EMPTY, null, "NONE", null, null,
                        List.of(), null, List.of()));

        mockMvc.perform(get("/api/v1/coach/readings/workout")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"));
    }

    @Test
    void ignoresATimezoneHeaderContainingASpace() throws Exception {
        mockMvc.perform(get("/api/v1/coach/readings/workout")
                        .header("X-Timezone", "Asia/Singa pore")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(readingService).workoutReading(eq(USER_ID), any(String.class), eq("UTC"));
    }

    @Test
    void ignoresATimezoneHeaderOverSixtyFourCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/coach/readings/workout")
                        .header("X-Timezone", "A".repeat(65))
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(readingService).workoutReading(eq(USER_ID), any(String.class), eq("UTC"));
    }

    @Test
    void surfacesAnUnavailableUpstreamAsServiceUnavailable() throws Exception {
        when(readingService.workoutReading(eq(USER_ID), any(String.class), eq("UTC")))
                .thenThrow(new ReadingUnavailableException("The workout service could not be read."));

        mockMvc.perform(get("/api/v1/coach/readings/workout")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("READING_UNAVAILABLE"));
    }

    @Test
    void nutritionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/coach/readings/nutrition")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheNutritionReadingForAnAuthenticatedUser() throws Exception {
        when(readingService.nutritionReading(eq(USER_ID), any(String.class), eq("Asia/Singapore")))
                .thenReturn(new CoachReadingResponse(
                        ReadingSurface.NUTRITION, ReadingStatus.READY, "9f2c4e1ab03d77aa",
                        "PREVIOUS_WEEK", null, null, List.of(),
                        new ModelReadingView("Your protein was spread across the day.",
                                "You ate 150 g of protein a day [1].",
                                List.of(1), "RULES", Instant.parse("2026-09-28T12:00:00Z")),
                        List.of()));

        mockMvc.perform(get("/api/v1/coach/readings/nutrition")
                        .header("X-Timezone", "Asia/Singapore")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surface").value("NUTRITION"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.reading.source").value("RULES"));
    }

    @Test
    void nutritionSurfacesAnUnavailableUpstream() throws Exception {
        when(readingService.nutritionReading(eq(USER_ID), any(String.class), eq("UTC")))
                .thenThrow(new ReadingUnavailableException("The nutrition service could not be read."));
        mockMvc.perform(get("/api/v1/coach/readings/nutrition")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("READING_UNAVAILABLE"));
    }

    @Test
    void progressRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/coach/readings/progress")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheProgressReadingForAnAuthenticatedUser() throws Exception {
        when(readingService.progressReading(eq(USER_ID), any(String.class), eq("UTC")))
                .thenReturn(new CoachReadingResponse(
                        ReadingSurface.PROGRESS, ReadingStatus.EMPTY, null,
                        "LAST_28_DAYS", null, null, List.of(), null, List.of()));

        mockMvc.perform(get("/api/v1/coach/readings/progress")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surface").value("PROGRESS"))
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.period").value("LAST_28_DAYS"));
    }

    @Test
    void progressSurfacesAnUnavailableUpstream() throws Exception {
        when(readingService.progressReading(eq(USER_ID), any(String.class), eq("UTC")))
                .thenThrow(new ReadingUnavailableException("The user service could not be read."));
        mockMvc.perform(get("/api/v1/coach/readings/progress")
                        .header("Authorization", "Bearer token")
                        .with(authentication(authToken())))
                .andExpect(status().isServiceUnavailable());
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                USER_ID, "user@example.com", "User", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
