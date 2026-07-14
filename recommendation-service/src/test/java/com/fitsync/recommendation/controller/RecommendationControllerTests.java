package com.fitsync.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.security.JwtService;
import com.fitsync.recommendation.service.RecommendationResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RecommendationController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class RecommendationControllerTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationResolver resolver;

    @MockBean
    private JwtService jwtService;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsPhrasedRecommendationsForTheCurrentUser() throws Exception {
        when(resolver.resolve(USER_ID)).thenReturn(List.of(result("WEEKLY_ADHERENCE",
                "{\"facts\":{\"targetDaysPerWeek\":4,\"activeDays\":2},\"narrative\":\"2 of 4 days done.\"}")));

        mockMvc.perform(get("/api/v1/recommendations")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("WEEKLY_ADHERENCE"))
                .andExpect(jsonPath("$[0].source").value("RULE"))
                .andExpect(jsonPath("$[0].facts.activeDays").value(2))
                .andExpect(jsonPath("$[0].narrative").value("2 of 4 days done."));
    }

    @Test
    void singleTypeIsCaseInsensitiveAndServedThroughTheResolver() throws Exception {
        when(resolver.resolve(eq(USER_ID), eq("STREAK_NUDGE"))).thenReturn(Optional.of(result(
                "STREAK_NUDGE", "{\"facts\":{\"streakDays\":7},\"narrative\":\"7-day streak!\"}")));

        mockMvc.perform(get("/api/v1/recommendations/streak_nudge")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("STREAK_NUDGE"))
                .andExpect(jsonPath("$.narrative").value("7-day streak!"));
    }

    @Test
    void unknownTypeIsNotFound() throws Exception {
        when(resolver.resolve(eq(USER_ID), any(String.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/recommendations/NUTRITION_GAP")
                        .with(authentication(authToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void refreshRecomputesAndReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/refresh")
                        .with(csrf())
                        .with(authentication(authToken())))
                .andExpect(status().isAccepted());

        verify(resolver).refresh(USER_ID);
    }

    private static RecommendationResultEntity result(String type, String payload) {
        RecommendationResultEntity entity = new RecommendationResultEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(USER_ID);
        entity.setType(type);
        entity.setSource(RecommendationSource.RULE);
        entity.setPayload(payload);
        entity.setGeneratedAt(Instant.parse("2026-07-06T12:00:00Z"));
        entity.setExpiresAt(Instant.parse("2026-07-07T12:00:00Z"));
        return entity;
    }

    private static UsernamePasswordAuthenticationToken authToken() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                USER_ID, "user@example.com", "User", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
