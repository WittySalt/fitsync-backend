package com.fitsync.recommendation.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.CatalogueClient.DownstreamWriteException;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.draft.CandidateSelector;
import com.fitsync.recommendation.coach.draft.DraftContextAssembler;
import com.fitsync.recommendation.coach.draft.DraftExercise;
import com.fitsync.recommendation.coach.draft.SessionDraft;
import com.fitsync.recommendation.coach.draft.SessionDraftGenerator;
import com.fitsync.recommendation.coach.draft.TemplateSessionBuilder;
import com.fitsync.recommendation.coach.draft.WorkoutPlanMapper;
import com.fitsync.recommendation.coach.dto.AcceptSessionDraftResponse;
import com.fitsync.recommendation.coach.dto.SessionDraftView;
import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.ArtifactStatus;
import com.fitsync.recommendation.coach.entity.DraftSource;
import com.fitsync.recommendation.coach.entity.GeneratedArtifactEntity;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Accepting and rejecting, against a scripted workout-service.
 *
 * <p>What matters here is the sequence and what survives a failure in the middle of it. A plan is
 * created and then started; if the second call fails the plan is kept and the response says the
 * session did not start, because undoing the plan would take something real away from the user to
 * keep a status field tidy. And accepting twice must not create two plans.
 */
class SessionDraftAcceptTests {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ARTIFACT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SESSION = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final CoachCallContext CALL = new CoachCallContext(USER, "token", null);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);

    private final WorkoutCatalogueClient workoutClient = mock(WorkoutCatalogueClient.class);
    private final GeneratedArtifactRepository repository = mock(GeneratedArtifactRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SessionDraftService service;

    @BeforeEach
    void wire() {
        service = new SessionDraftService(
                workoutClient,
                mock(CandidateSelector.class),
                mock(DraftContextAssembler.class),
                mock(SessionDraftGenerator.class),
                mock(TemplateSessionBuilder.class),
                repository,
                new WorkoutPlanMapper(),
                objectMapper,
                CLOCK);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("accepting creates the plan, starts the session, and records both on the draft")
    void acceptsAndStartsASession() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.createPlan(eq("token"), any())).thenReturn("{\"planId\":\"" + PLAN + "\"}");
        when(workoutClient.startPlan("token", PLAN)).thenReturn("{\"sessionId\":\"" + SESSION + "\"}");

        AcceptSessionDraftResponse response = service.accept(CALL, ARTIFACT);

        assertThat(response.planId()).isEqualTo(PLAN);
        assertThat(response.sessionId()).isEqualTo(SESSION);
        assertThat(response.sessionStarted()).isTrue();
        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.ACCEPTED);
        assertThat(artifact.getAcceptedRef()).isEqualTo(PLAN);
        assertThat(artifact.getStartedSessionRef()).isEqualTo(SESSION);
        assertThat(artifact.getAcceptedAt()).isEqualTo(Instant.parse("2026-09-26T12:00:00Z"));
    }

    @Test
    @DisplayName("the plan is created from the stored draft, with the caller's own token")
    void createsThePlanFromTheDraft() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.createPlan(eq("token"), any())).thenReturn("{\"planId\":\"" + PLAN + "\"}");
        when(workoutClient.startPlan("token", PLAN)).thenReturn("{\"sessionId\":\"" + SESSION + "\"}");

        service.accept(CALL, ARTIFACT);

        org.mockito.ArgumentCaptor<Object> body = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(workoutClient).createPlan(eq("token"), body.capture());
        assertThat(objectMapper.writeValueAsString(body.getValue()))
                .contains("\"name\":\"Upper body push\"")
                .contains("\"targetReps\":8");
    }

    @Test
    @DisplayName("if the session cannot be started, the plan is kept and the response says so")
    void keepsThePlanWhenTheStartFails() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.createPlan(eq("token"), any())).thenReturn("{\"planId\":\"" + PLAN + "\"}");
        when(workoutClient.startPlan("token", PLAN))
                .thenThrow(new DownstreamWriteException("workout", 409, "{\"error\":\"already active\"}"));

        AcceptSessionDraftResponse response = service.accept(CALL, ARTIFACT);

        assertThat(response.planId()).isEqualTo(PLAN);
        assertThat(response.sessionId()).isNull();
        assertThat(response.sessionStarted())
                .withFailMessage("the client must be told to fall back to the plan page")
                .isFalse();
        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.ACCEPTED);
        assertThat(artifact.getAcceptedRef()).isEqualTo(PLAN);
    }

    @Test
    @DisplayName("a workout already in progress stops the accept before anything is created")
    void refusesToAcceptWhileAWorkoutIsInProgress() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.activeSession("token")).thenReturn("{\"session\":{},\"resume\":{}}");

        assertThatThrownBy(() -> service.accept(CALL, ARTIFACT))
                .isInstanceOf(SessionDraftService.DraftNotAcceptableException.class)
                .hasMessage("You have a workout in progress. Finish or end it first.");
        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.DRAFT);
        verify(workoutClient, never()).createPlan(any(), any());
        verify(workoutClient, never()).startPlan(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a workout that begins during the accept takes the new plan back and leaves the draft open")
    void takesThePlanBackWhenAWorkoutBeginsMeanwhile() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.createPlan(eq("token"), any())).thenReturn("{\"planId\":\"" + PLAN + "\"}");
        when(workoutClient.startPlan("token", PLAN)).thenThrow(new DownstreamWriteException(
                "workout", 409, "{\"code\":\"ACTIVE_SESSION_EXISTS\",\"message\":\"You have a workout in progress.\"}"));

        assertThatThrownBy(() -> service.accept(CALL, ARTIFACT))
                .isInstanceOf(SessionDraftService.DraftNotAcceptableException.class)
                .hasMessage("You have a workout in progress. Finish or end it first.");
        verify(workoutClient).archivePlan("token", PLAN);
        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.DRAFT);
        assertThat(artifact.getAcceptedRef()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("the person gets the same sentence even when the new plan cannot be archived")
    void answersTheSameWhenTheArchiveFails() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.createPlan(eq("token"), any())).thenReturn("{\"planId\":\"" + PLAN + "\"}");
        when(workoutClient.startPlan("token", PLAN)).thenThrow(new DownstreamWriteException(
                "workout", 409, "{\"code\":\"ACTIVE_SESSION_EXISTS\"}"));
        org.mockito.Mockito.doThrow(new DownstreamWriteException("workout", 0, null))
                .when(workoutClient).archivePlan("token", PLAN);

        assertThatThrownBy(() -> service.accept(CALL, ARTIFACT))
                .isInstanceOf(SessionDraftService.DraftNotAcceptableException.class)
                .hasMessage("You have a workout in progress. Finish or end it first.");
    }

    @Test
    @DisplayName("if the plan cannot be created nothing is recorded and the caller gets a 409")
    void failsCleanlyWhenThePlanIsRefused() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));
        when(workoutClient.createPlan(eq("token"), any()))
                .thenThrow(new DownstreamWriteException("workout", 400, "{\"error\":\"bad\"}"));

        assertThatThrownBy(() -> service.accept(CALL, ARTIFACT))
                .isInstanceOf(SessionDraftService.DraftNotAcceptableException.class)
                .hasMessageContaining("400");
        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.DRAFT);
        verify(workoutClient, never()).startPlan(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("accepting twice returns what the first accept produced, and creates nothing")
    void isIdempotent() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.ACCEPTED);
        artifact.setAcceptedRef(PLAN);
        artifact.setStartedSessionRef(SESSION);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));

        AcceptSessionDraftResponse response = service.accept(CALL, ARTIFACT);

        assertThat(response.planId()).isEqualTo(PLAN);
        assertThat(response.sessionId()).isEqualTo(SESSION);
        verify(workoutClient, never()).createPlan(any(), any());
    }

    @Test
    @DisplayName("a rejected draft cannot be accepted")
    void refusesARejectedDraft() throws Exception {
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER))
                .thenReturn(Optional.of(storedDraft(ArtifactStatus.DISCARDED)));

        assertThatThrownBy(() -> service.accept(CALL, ARTIFACT))
                .isInstanceOf(SessionDraftService.DraftNotAcceptableException.class);
        verify(workoutClient, never()).createPlan(any(), any());
    }

    @Test
    @DisplayName("another user's draft is a 404, not a 403, so its existence is not revealed")
    void hidesOtherUsersDrafts() {
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(CALL, ARTIFACT))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("rejecting keeps the draft and its reason rather than deleting it")
    void rejectsWithAReason() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));

        SessionDraftView view = service.reject(USER, ARTIFACT, "  Too long for today.  ");

        assertThat(view.status()).isEqualTo(ArtifactStatus.DISCARDED);
        assertThat(artifact.getRejectionReason()).isEqualTo("Too long for today.");
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("a rejection without a reason is still a rejection")
    void rejectsWithoutAReason() throws Exception {
        GeneratedArtifactEntity artifact = storedDraft(ArtifactStatus.DRAFT);
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER)).thenReturn(Optional.of(artifact));

        service.reject(USER, ARTIFACT, "   ");

        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.DISCARDED);
        assertThat(artifact.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("an accepted draft cannot be rejected; the plan it became is the thing to discard")
    void refusesToRejectAnAcceptedDraft() throws Exception {
        when(repository.findByArtifactIdAndUserId(ARTIFACT, USER))
                .thenReturn(Optional.of(storedDraft(ArtifactStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.reject(USER, ARTIFACT, "changed my mind"))
                .isInstanceOf(SessionDraftService.DraftNotAcceptableException.class);
    }

    // -------------------------------------------------------------------------------------------

    private GeneratedArtifactEntity storedDraft(ArtifactStatus status) throws Exception {
        SessionDraft draft = new SessionDraft(
                "Upper body push", List.of("CHEST"), 42,
                List.of(new DraftExercise(
                        UUID.fromString("00000000-0000-0000-0000-00000000000b"),
                        "Barbell Bench Press", 4, 8, null, 90, "Main pressing movement.")),
                "Because you have not pressed this week.");
        GeneratedArtifactEntity artifact = new GeneratedArtifactEntity();
        artifact.setArtifactId(ARTIFACT);
        artifact.setUserId(USER);
        artifact.setKind(ArtifactKind.SESSION_DRAFT);
        artifact.setStatus(status);
        artifact.setSource(DraftSource.MODEL);
        artifact.setValidationAttempts(1);
        artifact.setPayload(objectMapper.writeValueAsString(draft));
        return artifact;
    }
}
