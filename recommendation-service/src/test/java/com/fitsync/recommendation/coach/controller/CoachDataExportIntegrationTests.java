package com.fitsync.recommendation.coach.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachThreadView;
import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.ArtifactStatus;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.entity.DraftSource;
import com.fitsync.recommendation.coach.entity.GeneratedArtifactEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingRepository;
import com.fitsync.recommendation.coach.reading.ReadingOutcome;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The coach part of the account data export, end to end through security, the controller, the
 * service and the database.
 *
 * <p>The point of the test is ownership. Two people have coach data side by side, and the export
 * for one of them must carry all of theirs, in order, and nothing of the other's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoachDataExportIntegrationTests {

    private static final UUID ALICE = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("b2222222-2222-2222-2222-222222222222");
    private static final UUID NOBODY = UUID.fromString("c3333333-3333-3333-3333-333333333333");

    private static final String DRAFT_PAYLOAD = "{\"title\":\"Push day\",\"focus\":[\"chest\"],"
            + "\"estimatedMinutes\":30,\"exercises\":[],\"summary\":\"A short push session.\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CoachThreadService threadService;

    @Autowired
    private GeneratedArtifactRepository artifactRepository;

    @Autowired
    private CoachPageReadingRepository readingRepository;

    @Autowired
    private RecommendationResultRepository resultRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("the export carries the caller's threads and turns in order, and nothing of anyone else's")
    void exportsOnlyTheCallersData() throws Exception {
        CoachThreadView first = threadService.createThread(ALICE, null);
        threadService.appendTurn(ALICE, first.threadId(), CoachTurnRole.USER, "Should I train today?");
        threadService.appendTurn(ALICE, first.threadId(), CoachTurnRole.TOOL, "{\"raw\":\"tool output\"}");
        threadService.appendTurn(ALICE, first.threadId(), CoachTurnRole.ASSISTANT, "Yes, a light session.");
        threadService.appendTurn(ALICE, first.threadId(), CoachTurnRole.USER, "How long?");
        threadService.appendTurn(ALICE, first.threadId(), CoachTurnRole.ASSISTANT, "About thirty minutes.");

        CoachThreadView second = threadService.createThread(ALICE, "Archived chat");
        threadService.appendTurn(ALICE, second.threadId(), CoachTurnRole.USER, "What about food?");
        threadService.archiveThread(ALICE, second.threadId());

        CoachThreadView bobs = threadService.createThread(BOB, null);
        threadService.appendTurn(BOB, bobs.threadId(), CoachTurnRole.USER, "Bob private question");
        threadService.appendTurn(BOB, bobs.threadId(), CoachTurnRole.ASSISTANT, "Bob private answer");

        artifactRepository.save(draft(ALICE));
        artifactRepository.save(draft(BOB));
        readingRepository.save(reading(ALICE, "Alice reading headline"));
        readingRepository.save(reading(BOB, "Bob reading headline"));
        resultRepository.save(result(ALICE, "{\"tip\":\"alice\"}"));
        resultRepository.save(result(BOB, "{\"tip\":\"bob\"}"));
        entityManager.flush();
        entityManager.clear();

        String live = "$.threads[?(@.threadId == '" + first.threadId() + "')]";
        String archived = "$.threads[?(@.threadId == '" + second.threadId() + "')]";
        mockMvc.perform(get("/api/v1/coach/me/export").with(authentication(principal(ALICE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("recommendation-service"))
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.threads", hasSize(2)))
                // Threads are listed oldest first, but two threads created in the same instant tie,
                // so each one is picked out by id rather than by position.
                .andExpect(jsonPath(live + ".title", contains("Should I train today?")))
                .andExpect(jsonPath(live + ".archived", contains(false)))
                .andExpect(jsonPath(live + ".turns[*].role", contains("USER", "ASSISTANT", "USER", "ASSISTANT")))
                .andExpect(jsonPath(live + ".turns[*].content", contains(
                        "Should I train today?", "Yes, a light session.", "How long?", "About thirty minutes.")))
                .andExpect(jsonPath(archived + ".archived", contains(true)))
                .andExpect(jsonPath(archived + ".turns[*].content", contains("What about food?")))
                .andExpect(jsonPath("$.sessionDrafts", hasSize(1)))
                .andExpect(jsonPath("$.sessionDrafts[0].session.title").value("Push day"))
                .andExpect(jsonPath("$.readings", hasSize(1)))
                .andExpect(jsonPath("$.readings[0].headline").value("Alice reading headline"))
                .andExpect(jsonPath("$.recommendations", hasSize(1)))
                .andExpect(jsonPath("$.recommendations[0].payload").value("{\"tip\":\"alice\"}"))
                .andExpect(content().string(not(containsString("Bob"))))
                .andExpect(content().string(not(containsString(bobs.threadId().toString()))))
                .andExpect(content().string(not(containsString("tool output"))));
    }

    @Test
    @DisplayName("an account with no coach data gets empty lists rather than an error")
    void emptyAccountGetsEmptyLists() throws Exception {
        mockMvc.perform(get("/api/v1/coach/me/export").with(authentication(principal(NOBODY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("recommendation-service"))
                .andExpect(jsonPath("$.threads", hasSize(0)))
                .andExpect(jsonPath("$.sessionDrafts", hasSize(0)))
                .andExpect(jsonPath("$.readings", hasSize(0)))
                .andExpect(jsonPath("$.recommendations", hasSize(0)));
    }

    @Test
    @DisplayName("the export needs a signed-in user")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/coach/me/export"))
                .andExpect(status().isUnauthorized());
    }

    private static GeneratedArtifactEntity draft(UUID userId) {
        GeneratedArtifactEntity artifact = new GeneratedArtifactEntity();
        artifact.setArtifactId(UUID.randomUUID());
        artifact.setUserId(userId);
        artifact.setKind(ArtifactKind.SESSION_DRAFT);
        artifact.setStatus(ArtifactStatus.DRAFT);
        artifact.setSource(DraftSource.MODEL);
        artifact.setValidationAttempts(1);
        artifact.setPayload(DRAFT_PAYLOAD);
        return artifact;
    }

    private static CoachPageReadingEntity reading(UUID userId, String headline) {
        CoachPageReadingEntity reading = new CoachPageReadingEntity();
        reading.setId(UUID.randomUUID());
        reading.setUserId(userId);
        reading.setSurface(ReadingSurface.WORKOUT);
        reading.setFactsHash("hash");
        reading.setOutcome(ReadingOutcome.ACCEPTED);
        reading.setHeadline(headline);
        reading.setBody("A reading body.");
        reading.setStartedAt(Instant.parse("2026-10-01T08:00:00Z"));
        reading.setGeneratedAt(Instant.parse("2026-10-01T08:00:02Z"));
        return reading;
    }

    private static RecommendationResultEntity result(UUID userId, String payload) {
        RecommendationResultEntity result = new RecommendationResultEntity();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setType("DAILY_TIP");
        result.setSource(RecommendationSource.RULE);
        result.setPayload(payload);
        result.setGeneratedAt(Instant.parse("2026-10-01T08:00:00Z"));
        return result;
    }

    private static UsernamePasswordAuthenticationToken principal(UUID userId) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "user@example.com", "User", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
