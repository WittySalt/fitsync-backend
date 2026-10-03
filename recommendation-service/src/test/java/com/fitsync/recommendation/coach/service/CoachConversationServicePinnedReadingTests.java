package com.fitsync.recommendation.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.dto.CoachMessageContext;
import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.dto.CoachSurface;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachOrchestrator;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.orchestrator.CoachProgressListener;
import com.fitsync.recommendation.coach.orchestrator.TurnContext;
import com.fitsync.recommendation.coach.reading.CoachReadingService;
import com.fitsync.recommendation.coach.reading.ReadingFact;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * How a message's page and reading id become the context the orchestrator sees: each page that shows
 * a reading resolves its own, and a page that shows none never asks for one.
 */
class CoachConversationServicePinnedReadingTests {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID THREAD_ID = UUID.randomUUID();
    private static final String TOKEN = "the-caller-credential";
    private static final String READING_ID = "0123456789abcdef";

    private CoachThreadService threadService;
    private CoachOrchestrator orchestrator;
    private CoachReadingService readingService;
    private CoachConversationService service;

    @BeforeEach
    void wire() {
        threadService = mock(CoachThreadService.class);
        orchestrator = mock(CoachOrchestrator.class);
        readingService = mock(CoachReadingService.class);
        CoachTurnEntity turn = mock(CoachTurnEntity.class);
        when(turn.getTurnId()).thenReturn(UUID.randomUUID());
        when(threadService.appendTurn(any(), any(), any(CoachTurnRole.class), anyString())).thenReturn(turn);
        when(threadService.contextWindow(any())).thenReturn(List.of());
        when(orchestrator.answer(any(), anyString(), any(), any(), any(CoachProgressListener.class)))
                .thenReturn(new CoachOutcome("An answer.", 2, List.of(), 10, true, "PASSED"));
        service = new CoachConversationService(threadService, orchestrator, readingService,
                mock(CoachProperties.class), mock(ExecutorService.class), mock(CoachRateLimiter.class));
    }

    @Test
    @DisplayName("a nutrition message resolves the nutrition reading and reports its id")
    void nutritionReadingIsResolved() {
        when(readingService.pinnedReading(eq(ReadingSurface.NUTRITION), eq(USER_ID), eq(TOKEN), eq("Asia/Singapore"),
                eq(READING_ID))).thenReturn(Optional.of(pinned("PREVIOUS_WEEK")));

        CoachMessageResponse response = send(CoachSurface.NUTRITION);

        TurnContext context = orchestratorContext();
        assertThat(context.surface()).isEqualTo(CoachSurface.NUTRITION);
        assertThat(context.pinnedReading()).isNotNull();
        assertThat(context.pinnedReading().facts()).hasSize(1);
        assertThat(context.pinnedReading().period().currentWeek()).isFalse();
        assertThat(response.pinnedReadingId()).isEqualTo(READING_ID);
    }

    @Test
    @DisplayName("a progress message resolves the progress reading, whose 28 days are not described as a week")
    void progressReadingIsResolvedWithoutAWeek() {
        when(readingService.pinnedReading(eq(ReadingSurface.PROGRESS), eq(USER_ID), eq(TOKEN), eq("Asia/Singapore"),
                eq(READING_ID))).thenReturn(Optional.of(pinned("LAST_28_DAYS")));

        send(CoachSurface.PROGRESS);

        TurnContext context = orchestratorContext();
        assertThat(context.pinnedReading()).isNotNull();
        assertThat(context.pinnedReading().period()).isNull();
    }

    @Test
    @DisplayName("a workout message still resolves the workout reading")
    void workoutReadingIsResolved() {
        when(readingService.pinnedReading(eq(ReadingSurface.WORKOUT), eq(USER_ID), eq(TOKEN), eq("Asia/Singapore"),
                eq(READING_ID))).thenReturn(Optional.of(pinned("THIS_WEEK")));

        send(CoachSurface.WORKOUT);

        assertThat(orchestratorContext().pinnedReading().period().currentWeek()).isTrue();
    }

    @Test
    @DisplayName("the recipe id of the context reaches the turn context, and is null when the client sent none")
    void recipeIdIsCarriedIntoTheTurnContext() {
        String id = "6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb";

        service.send(USER_ID, THREAD_ID, TOKEN, "Is this a good dinner?",
                new CoachMessageContext(CoachSurface.NUTRITION, null, "Asia/Singapore", id));
        assertThat(orchestratorContext().recipeId()).isEqualTo(id);

        send(CoachSurface.NUTRITION);
        assertThat(orchestratorContext().recipeId()).isNull();
    }

    @Test
    @DisplayName("pages that show no reading never ask for one")
    void otherPagesAskForNothing() {
        send(CoachSurface.HOME);
        send(CoachSurface.PROFILE);

        verify(readingService, never()).pinnedReading(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a reading service failure does not fail the turn")
    void failureDoesNotFailTheTurn() {
        when(readingService.pinnedReading(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        CoachMessageResponse response = send(CoachSurface.NUTRITION);

        assertThat(response.answer()).isEqualTo("An answer.");
        assertThat(orchestratorContext().pinnedReading()).isNull();
    }

    private CoachMessageResponse send(CoachSurface surface) {
        return service.send(USER_ID, THREAD_ID, TOKEN, "How did I do?",
                new CoachMessageContext(surface, READING_ID, "Asia/Singapore"));
    }

    private TurnContext orchestratorContext() {
        ArgumentCaptor<TurnContext> captor = ArgumentCaptor.forClass(TurnContext.class);
        verify(orchestrator, org.mockito.Mockito.atLeastOnce())
                .answer(any(), anyString(), any(), captor.capture(), any(CoachProgressListener.class));
        return captor.getValue();
    }

    private static CoachReadingService.PinnedReading pinned(String period) {
        ReadingFact fact = new ReadingFact(1, "PROTEIN_PER_DAY", "Protein a day", "150", "g",
                new BigDecimal("150"), "", "");
        return new CoachReadingService.PinnedReading(READING_ID, "A headline.", "A body [1].", "RULES",
                List.of(fact), period, LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"));
    }
}
