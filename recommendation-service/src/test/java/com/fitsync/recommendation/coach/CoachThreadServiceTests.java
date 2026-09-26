package com.fitsync.recommendation.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachThreadDetailView;
import com.fitsync.recommendation.coach.dto.CoachThreadView;
import com.fitsync.recommendation.coach.dto.CoachTurnView;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversation spine: ordering, ownership, the context window and the trace.
 *
 * <p>No model is involved. That is the point of building this layer first — everything the
 * orchestrator will rely on can be proven before a model is in the loop, so a later failure is
 * unambiguously the model's and not the plumbing's.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachThreadServiceTests {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private CoachThreadService service;

    @Test
    @DisplayName("a thread names itself from its opening question")
    void threadDerivesTitleFromFirstUserTurn() {
        CoachThreadView thread = service.createThread(ALICE, null);
        assertThat(thread.title()).isNull();

        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "Should I train today?");

        assertThat(service.listThreads(ALICE))
                .singleElement()
                .extracting(CoachThreadView::title)
                .isEqualTo("Should I train today?");
    }

    @Test
    @DisplayName("a long opening question is cut on a word boundary, not mid-word")
    void longTitleIsCutCleanly() {
        CoachThreadView thread = service.createThread(ALICE, null);
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER,
                "I want to understand whether my current training split is actually balanced "
                        + "across muscle groups or whether I am overworking my chest again");

        String title = service.listThreads(ALICE).get(0).title();
        assertThat(title).endsWith("…");
        assertThat(title).doesNotContain("  ");
        assertThat(title.substring(0, title.length() - 1).strip())
                .withFailMessage("a title should end on a whole word")
                .matches(".*\\w$");
    }

    @Test
    @DisplayName("an explicit title is kept and the first question does not overwrite it")
    void explicitTitleSurvives() {
        CoachThreadView thread = service.createThread(ALICE, "Planning this week");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "Should I train today?");

        assertThat(service.listThreads(ALICE).get(0).title()).isEqualTo("Planning this week");
    }

    @Test
    @DisplayName("turns are numbered from one and read back in order")
    void turnsAreOrdered() {
        CoachThreadView thread = service.createThread(ALICE, "Ordering");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "first");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.ASSISTANT, "second");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "third");

        CoachThreadDetailView detail = service.getThread(ALICE, thread.threadId());

        assertThat(detail.turns()).extracting(CoachTurnView::seq).containsExactly(1, 2, 3);
        assertThat(detail.turns()).extracting(CoachTurnView::content)
                .containsExactly("first", "second", "third");
        assertThat(detail.thread().turnCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("another user's thread is indistinguishable from one that does not exist")
    void threadsAreScopedToTheirOwner() {
        CoachThreadView aliceThread = service.createThread(ALICE, "Private");

        assertThatThrownBy(() -> service.getThread(BOB, aliceThread.threadId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.appendTurn(BOB, aliceThread.threadId(), CoachTurnRole.USER, "hello"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.listThreads(BOB)).isEmpty();
    }

    @Test
    @DisplayName("the context window keeps the newest turns, in reading order")
    void contextWindowKeepsTheTail() {
        CoachThreadView thread = service.createThread(ALICE, "Long");
        int total = CoachThreadService.CONTEXT_WINDOW_TURNS + 5;
        for (int i = 1; i <= total; i++) {
            service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "message " + i);
        }

        List<CoachTurnEntity> window = service.contextWindow(thread.threadId());

        assertThat(window).hasSize(CoachThreadService.CONTEXT_WINDOW_TURNS);
        assertThat(window.get(0).getSeq())
                .withFailMessage("the window should start at the oldest turn it kept")
                .isEqualTo(total - CoachThreadService.CONTEXT_WINDOW_TURNS + 1);
        assertThat(window.get(window.size() - 1).getSeq()).isEqualTo(total);
        assertThat(window).extracting(CoachTurnEntity::getSeq).isSorted();
    }

    @Test
    @DisplayName("a short thread returns entirely, without padding")
    void contextWindowOnAShortThread() {
        CoachThreadView thread = service.createThread(ALICE, "Short");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "only message");

        assertThat(service.contextWindow(thread.threadId())).hasSize(1);
    }

    @Test
    @DisplayName("telemetry and tool calls come back attached to the turn that produced them")
    void telemetryAndToolsAreRecorded() {
        CoachThreadView thread = service.createThread(ALICE, "Trace");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "build me a session");
        CoachTurnEntity answer =
                service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.ASSISTANT, "here it is");

        service.recordToolInvocation(answer.getTurnId(), "readFacts", "{}", "12 facts", 4, true, null);
        service.recordToolInvocation(answer.getTurnId(), "searchExercises",
                "{\"muscle\":\"CHEST\"}", "20 candidates", 23, true, null);
        service.recordTelemetry(answer.getTurnId(), 2, "llama3.2:3b", 312, 96, 2410, "PASSED");

        CoachTurnView view = service.getThread(ALICE, thread.threadId()).turns().get(1);

        assertThat(view.tier()).isEqualTo(2);
        assertThat(view.model()).isEqualTo("llama3.2:3b");
        assertThat(view.promptTokens()).isEqualTo(312);
        assertThat(view.latencyMs()).isEqualTo(2410);
        assertThat(view.guardrailVerdict()).isEqualTo("PASSED");
        assertThat(view.tools()).extracting("tool").containsExactly("readFacts", "searchExercises");
        assertThat(view.tools()).extracting("seq").containsExactly(1, 2);
        assertThat(view.tools().get(1).resultSummary()).isEqualTo("20 candidates");
    }

    @Test
    @DisplayName("a user turn carries no telemetry, so a tier of 0 or 1 stays meaningful")
    void userTurnsHaveNoTelemetry() {
        CoachThreadView thread = service.createThread(ALICE, "Telemetry");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "question");

        CoachTurnView view = service.getThread(ALICE, thread.threadId()).turns().get(0);

        assertThat(view.tier()).isNull();
        assertThat(view.model()).isNull();
        assertThat(view.tools()).isEmpty();
    }

    @Test
    @DisplayName("an archived thread leaves the list but keeps its turns")
    void archivingHidesWithoutDeleting() {
        CoachThreadView thread = service.createThread(ALICE, "Done with this");
        service.appendTurn(ALICE, thread.threadId(), CoachTurnRole.USER, "a question");

        service.archiveThread(ALICE, thread.threadId());

        assertThat(service.listThreads(ALICE)).isEmpty();
        assertThat(service.getThread(ALICE, thread.threadId()).turns())
                .withFailMessage("the evaluation harness still needs the telemetry on these turns")
                .hasSize(1);
    }

    @Test
    @DisplayName("threads list most recently active first")
    void listIsOrderedByActivity() {
        CoachThreadView older = service.createThread(ALICE, "Older");
        CoachThreadView newer = service.createThread(ALICE, "Newer");
        service.appendTurn(ALICE, older.threadId(), CoachTurnRole.USER, "bumping the older one");

        assertThat(service.listThreads(ALICE)).extracting(CoachThreadView::title)
                .containsExactly("Older", "Newer");
    }
}
