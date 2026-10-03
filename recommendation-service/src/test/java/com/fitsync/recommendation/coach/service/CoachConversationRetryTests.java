package com.fitsync.recommendation.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachMessageContext;
import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.dto.CoachSurface;
import com.fitsync.recommendation.coach.dto.CoachThreadDetailView;
import com.fitsync.recommendation.coach.dto.CoachTurnView;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachOrchestrator;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.orchestrator.CoachProgressListener;
import com.fitsync.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * A retried question, a failed run and the per-user limit, against the real conversation spine and
 * database with only the model-driven orchestrator replaced. Each test uses its own user, so the
 * counts the limit reads never leak from one test into another.
 */
@SpringBootTest(properties = {
        "fitsync.coach.turns-per-hour=4",
        "fitsync.coach.stream-timeout=PT0.6S"
})
@ActiveProfiles("test")
class CoachConversationRetryTests {

    private static final String TOKEN = "caller-credential";

    @Autowired
    private CoachConversationService conversation;

    @Autowired
    private CoachThreadService threads;

    @MockBean
    private CoachOrchestrator orchestrator;

    private UUID newThread(UUID user) {
        return threads.createThread(user, null).threadId();
    }

    private void orchestratorAnswers(String answer) {
        when(orchestrator.answer(any(), anyString(), any(), any(), any(CoachProgressListener.class)))
                .thenReturn(new CoachOutcome(answer, 2, List.of("readFacts"), 25, true, "PASSED"));
    }

    @Test
    @DisplayName("the same client turn id twice asks the model once and returns the same answer both times")
    void aRetryDoesNotDuplicateTheTurn() {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        UUID clientTurn = UUID.randomUUID();
        orchestratorAnswers("Down 0.41 kg a week.");

        CoachMessageResponse first = conversation.send(user, thread, TOKEN, "What changed?", null, clientTurn);
        CoachMessageResponse retry = conversation.send(user, thread, TOKEN, "What changed?", null, clientTurn);

        verify(orchestrator, times(1)).answer(any(), anyString(), any(), any(), any(CoachProgressListener.class));
        assertThat(retry.answer()).isEqualTo("Down 0.41 kg a week.");
        assertThat(retry.turnId()).isEqualTo(first.turnId());
        assertThat(retry.status()).isEqualTo(CoachMessageResponse.ANSWERED);
        CoachThreadDetailView detail = threads.getThread(user, thread);
        assertThat(detail.turns()).extracting(CoachTurnView::role).containsExactly("USER", "ASSISTANT");
    }

    @Test
    @DisplayName("without a client turn id every send is its own turn, as before")
    void withoutAnIdEverySendIsATurn() {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        orchestratorAnswers("Ok.");

        conversation.send(user, thread, TOKEN, "One", null);
        conversation.send(user, thread, TOKEN, "Two", null);

        assertThat(threads.getThread(user, thread).turns()).hasSize(4);
    }

    @Test
    @DisplayName("a failed run fills the assistant turn with the standard sentence and marks it FAILED, never leaving it empty")
    void aFailedRunIsNeverAnEmptyTurn() {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        UUID clientTurn = UUID.randomUUID();
        when(orchestrator.answer(any(), anyString(), any(), any(), any(CoachProgressListener.class)))
                .thenThrow(new IllegalStateException("model exploded"));

        assertThatThrownBy(() -> conversation.send(user, thread, TOKEN, "What changed?", null, clientTurn))
                .isInstanceOf(IllegalStateException.class);

        List<CoachTurnView> turns = threads.getThread(user, thread).turns();
        assertThat(turns).hasSize(2);
        assertThat(turns.get(1).content()).isEqualTo(CoachConversationService.FAILURE_TEXT);
        assertThat(turns.get(1).guardrailVerdict()).isEqualTo(CoachConversationService.FAILED_VERDICT);

        // The retry gets the failure that was recorded rather than a second attempt in the same slot.
        CoachMessageResponse retry = conversation.send(user, thread, TOKEN, "What changed?", null, clientTurn);
        assertThat(retry.answer()).isEqualTo(CoachConversationService.FAILURE_TEXT);
    }

    @Test
    @DisplayName("a retry of a turn that is still running gives up after the timeout with RUNNING and the turn id")
    void aStillRunningTurnReportsRunning() {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        UUID clientTurn = UUID.randomUUID();
        threads.appendTurn(user, thread, CoachTurnRole.USER, "What changed?", clientTurn, null);
        UUID answerTurn = threads.appendTurn(user, thread, CoachTurnRole.ASSISTANT, "").getTurnId();

        CoachMessageResponse retry = conversation.send(user, thread, TOKEN, "What changed?", null, clientTurn);

        assertThat(retry.status()).isEqualTo(CoachMessageResponse.RUNNING);
        assertThat(retry.turnId()).isEqualTo(answerTurn);
        assertThat(retry.answer()).isEmpty();
        verify(orchestrator, times(0)).answer(any(), anyString(), any(), any(), any(CoachProgressListener.class));
    }

    @Test
    @DisplayName("a retry that arrives while the first attempt finishes returns the answer it wrote")
    void aRetryWaitsForTheFirstAttempt() throws Exception {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        UUID clientTurn = UUID.randomUUID();
        threads.appendTurn(user, thread, CoachTurnRole.USER, "What changed?", clientTurn, null);
        UUID answerTurn = threads.appendTurn(user, thread, CoachTurnRole.ASSISTANT, "").getTurnId();
        Thread finisher = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            threads.replaceContent(answerTurn, "Finished while you waited.");
        });
        finisher.start();

        CoachMessageResponse retry = conversation.send(user, thread, TOKEN, "What changed?", null, clientTurn);
        finisher.join();

        assertThat(retry.answer()).isEqualTo("Finished while you waited.");
        assertThat(retry.status()).isEqualTo(CoachMessageResponse.ANSWERED);
    }

    @Test
    @DisplayName("another user's thread stays a 404 even with a client turn id that exists in it")
    void ownershipHoldsForARetry() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        UUID thread = newThread(owner);
        UUID clientTurn = UUID.randomUUID();
        orchestratorAnswers("Private.");
        conversation.send(owner, thread, TOKEN, "Mine", null, clientTurn);

        assertThatThrownBy(() -> conversation.send(intruder, thread, TOKEN, "Mine", null, clientTurn))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the fifth question in an hour is refused when the limit is four, and a retry of an earlier one is not")
    void theLimitCountsStartedTurnsOnly() {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        UUID firstId = UUID.randomUUID();
        orchestratorAnswers("Ok.");
        conversation.send(user, thread, TOKEN, "Q1", null, firstId);
        conversation.send(user, thread, TOKEN, "Q2", null);
        conversation.send(user, thread, TOKEN, "Q3", null);
        conversation.send(user, thread, TOKEN, "Q4", null);

        assertThatThrownBy(() -> conversation.send(user, thread, TOKEN, "Q5", null))
                .isInstanceOfSatisfying(CoachLimitException.class, ex -> {
                    assertThat(ex.retryAfterSeconds()).isPositive();
                    assertThat(ex.getMessage()).startsWith("You have asked a lot this hour. You can ask again at ");
                });
        assertThat(threads.getThread(user, thread).turns()).hasSize(8);

        CoachMessageResponse retry = conversation.send(user, thread, TOKEN, "Q1", null, firstId);
        assertThat(retry.answer()).isEqualTo("Ok.");
    }

    @Test
    @DisplayName("the limit is per user: someone else's questions do not count")
    void theLimitIsPerUser() {
        UUID busy = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        UUID busyThread = newThread(busy);
        UUID quietThread = newThread(quiet);
        orchestratorAnswers("Ok.");
        for (int i = 0; i < 4; i++) {
            conversation.send(busy, busyThread, TOKEN, "Q" + i, null);
        }

        assertThat(conversation.send(quiet, quietThread, TOKEN, "Hello", null).answer()).isEqualTo("Ok.");
    }

    @Test
    @DisplayName("the page a thread was started from is kept for the history list, from the opening message only")
    void theSurfaceIsRecorded() {
        UUID user = UUID.randomUUID();
        UUID thread = newThread(user);
        orchestratorAnswers("Ok.");

        conversation.send(user, thread, TOKEN, "Opening", new CoachMessageContext(CoachSurface.PROGRESS, null, null));
        conversation.send(user, thread, TOKEN, "Later", new CoachMessageContext(CoachSurface.HOME, null, null));

        assertThat(threads.listThreads(user, 0, 20).getContent())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.surface()).isEqualTo("PROGRESS");
                    assertThat(view.turnCount()).isEqualTo(4);
                });
    }
}
