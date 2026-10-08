package com.fitsync.recommendation.coach.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fitsync.recommendation.coach.dto.CoachThreadDetailView;
import com.fitsync.recommendation.coach.dto.CoachThreadView;
import com.fitsync.recommendation.coach.dto.CoachTurnView;
import com.fitsync.recommendation.coach.dto.ToolInvocationView;
import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.entity.ToolInvocationEntity;
import com.fitsync.recommendation.coach.repository.CoachThreadRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.ToolInvocationRepository;
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns conversation state: threads, their turns, and the tool calls made while answering.
 *
 * <p>The audit columns are not set here. {@code BaseAuditableEntity} is driven by Spring Data's
 * auditing listener, so created and updated stamps arrive on save.
 *
 * <p>No model is called from here. This is the spine the orchestrator will sit on, kept separate
 * so that persistence and ordering can be tested without a model in the loop.
 */
@Service
public class CoachThreadService {

    /**
     * How many turns of history the orchestrator gets. Twelve is six exchanges, which covers the
     * follow-up questions people actually ask without letting a long thread push the grounded
     * facts out of a small model's context.
     */
    public static final int CONTEXT_WINDOW_TURNS = 12;

    /** The most threads one page of the history list may hold. */
    public static final int MAX_PAGE_SIZE = 50;

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int DERIVED_TITLE_LENGTH = 60;

    /** The length of the tool invocation's result_summary and error columns. */
    private static final int MAX_INVOCATION_TEXT_LENGTH = 500;

    private final CoachThreadRepository threadRepository;
    private final CoachTurnRepository turnRepository;
    private final ToolInvocationRepository toolRepository;
    private final Clock clock;

    public CoachThreadService(CoachThreadRepository threadRepository,
                              CoachTurnRepository turnRepository,
                              ToolInvocationRepository toolRepository,
                              Clock clock) {
        this.threadRepository = threadRepository;
        this.turnRepository = turnRepository;
        this.toolRepository = toolRepository;
        this.clock = clock;
    }

    @Transactional
    public CoachThreadView createThread(UUID userId, String title) {
        Instant now = Instant.now(clock);
        CoachThreadEntity entity = new CoachThreadEntity();
        entity.setThreadId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setTitle(trimToLength(title, MAX_TITLE_LENGTH));
        entity.setArchived(false);
        entity.setLastActiveAt(now);
        threadRepository.save(entity);
        return toThreadView(entity, 0);
    }

    /** The first page of the caller's live threads, newest first. */
    @Transactional(readOnly = true)
    public List<CoachThreadView> listThreads(UUID userId) {
        return listThreads(userId, 0, MAX_PAGE_SIZE).getContent();
    }

    /**
     * One page of the caller's live threads, most recently used first, for the history list.
     * Archived threads never appear, and another user's threads are never reachable because the
     * query itself is scoped to the caller. A size above {@link #MAX_PAGE_SIZE} is cut to it.
     */
    @Transactional(readOnly = true)
    public Page<CoachThreadView> listThreads(UUID userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest request = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Order.desc("lastActiveAt"), Sort.Order.desc("dateTimeCreated")));
        return threadRepository.findByUserIdAndArchivedFalse(userId, request)
                .map(thread -> toThreadView(thread, turnRepository.findMaxSeq(thread.getThreadId())));
    }

    @Transactional(readOnly = true)
    public CoachThreadDetailView getThread(UUID userId, UUID threadId) {
        CoachThreadEntity thread = requireOwnedThread(userId, threadId);
        List<CoachTurnEntity> turns = turnRepository.findByThreadIdOrderBySeqAsc(threadId);

        // One query for every turn's tools rather than one per turn: a long thread would otherwise
        // issue a query per exchange purely to render its traces.
        Map<UUID, List<ToolInvocationEntity>> toolsByTurn = turns.isEmpty()
                ? Map.of()
                : toolRepository.findByTurnIdInOrderBySeqAsc(turns.stream().map(CoachTurnEntity::getTurnId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ToolInvocationEntity::getTurnId));

        List<CoachTurnView> views = turns.stream()
                .map(turn -> toTurnView(turn, toolsByTurn.getOrDefault(turn.getTurnId(), List.of())))
                .toList();
        return new CoachThreadDetailView(toThreadView(thread, turns.size()), views);
    }

    /**
     * Appends a turn and returns it.
     *
     * <p>The sequence number is allocated inside the transaction from the current maximum. A thread
     * is a single user's conversation, so two concurrent appends to the same thread would mean the
     * same person sending two messages at once; the unique constraint on {@code (thread_id, seq)}
     * turns that race into a failed write rather than two turns silently sharing a position.
     */
    @Transactional
    public CoachTurnEntity appendTurn(UUID userId, UUID threadId, CoachTurnRole role, String content) {
        return appendTurn(userId, threadId, role, content, null, null);
    }

    /**
     * Appends a turn that may carry the client's id for it and, on the opening question, the page
     * the thread was started from. A second turn with the same client id in the same thread is
     * refused by the database, which is what makes a retried question find its first attempt.
     */
    @Transactional
    public CoachTurnEntity appendTurn(UUID userId, UUID threadId, CoachTurnRole role, String content,
                                      UUID clientTurnId, String surface) {
        CoachThreadEntity thread = requireOwnedThread(userId, threadId);
        Instant now = Instant.now(clock);

        CoachTurnEntity turn = new CoachTurnEntity();
        turn.setTurnId(UUID.randomUUID());
        turn.setThreadId(threadId);
        turn.setSeq(turnRepository.findMaxSeq(threadId) + 1);
        turn.setRole(role);
        turn.setContent(content);
        turn.setClientTurnId(clientTurnId);
        turnRepository.saveAndFlush(turn);

        thread.setLastActiveAt(now);
        if (thread.getSurface() == null && surface != null) {
            thread.setSurface(surface);
        }
        if (thread.getTitle() == null && role == CoachTurnRole.USER) {
            thread.setTitle(deriveTitle(content));
        }
        threadRepository.save(thread);

        return turn;
    }

    /**
     * Fills in an assistant turn once its answer exists.
     *
     * <p>The turn is created empty before the orchestrator runs, because tool invocations are
     * children of a turn and have to be recorded as the loop makes them. This closes that gap.
     */
    @Transactional
    public void replaceContent(UUID turnId, String content) {
        CoachTurnEntity turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new ResourceNotFoundException("Turn not found"));
        turn.setContent(content);
        turnRepository.save(turn);
    }

    /** Records how an assistant turn was produced. Called by the orchestrator once the answer is ready. */
    @Transactional
    public void recordTelemetry(UUID turnId,
                                Integer tier,
                                String model,
                                Integer promptTokens,
                                Integer completionTokens,
                                Integer latencyMs,
                                String guardrailVerdict) {
        CoachTurnEntity turn = turnRepository.findById(turnId)
                .orElseThrow(() -> new ResourceNotFoundException("Turn not found"));
        turn.setTier(tier);
        turn.setModel(model);
        turn.setPromptTokens(promptTokens);
        turn.setCompletionTokens(completionTokens);
        turn.setLatencyMs(latencyMs);
        turn.setGuardrailVerdict(guardrailVerdict);
        turnRepository.save(turn);
    }

    @Transactional
    public void recordToolInvocation(UUID turnId,
                                     String tool,
                                     String argsJson,
                                     String resultSummary,
                                     int latencyMs,
                                     boolean ok,
                                     String error) {
        int seq = toolRepository.findByTurnIdOrderBySeqAsc(turnId).size() + 1;

        ToolInvocationEntity invocation = new ToolInvocationEntity();
        invocation.setInvocationId(UUID.randomUUID());
        invocation.setTurnId(turnId);
        invocation.setSeq(seq);
        invocation.setTool(tool);
        invocation.setArgsJson(argsJson);
        invocation.setResultSummary(trimToLength(resultSummary, MAX_INVOCATION_TEXT_LENGTH));
        invocation.setLatencyMs(latencyMs);
        invocation.setOk(ok);
        invocation.setError(trimToLength(error, MAX_INVOCATION_TEXT_LENGTH));
        toolRepository.save(invocation);
    }

    /**
     * The tail of a thread, oldest first, capped at {@link #CONTEXT_WINDOW_TURNS}.
     *
     * <p>Fetched newest-first so the database applies the cap, then reversed, because the model
     * needs the window in reading order.
     */
    @Transactional(readOnly = true)
    public List<CoachTurnEntity> contextWindow(UUID threadId) {
        List<CoachTurnEntity> newestFirst = turnRepository.findByThreadIdOrderBySeqDesc(threadId);
        List<CoachTurnEntity> window = new ArrayList<>(
                newestFirst.subList(0, Math.min(CONTEXT_WINDOW_TURNS, newestFirst.size())));
        window.sort(Comparator.comparingInt(CoachTurnEntity::getSeq));
        return window;
    }

    /** The user turn a client id was given to, or empty when this thread has no such turn. */
    @Transactional(readOnly = true)
    public Optional<CoachTurnEntity> findByClientTurnId(UUID userId, UUID threadId, UUID clientTurnId) {
        requireOwnedThread(userId, threadId);
        return turnRepository.findByThreadIdAndClientTurnId(threadId, clientTurnId);
    }

    /** The turn straight after another, which for a user turn is the answer that was opened for it. */
    @Transactional(readOnly = true)
    public Optional<CoachTurnEntity> turnAfter(UUID threadId, int seq) {
        return turnRepository.findByThreadIdAndSeq(threadId, seq + 1);
    }

    @Transactional(readOnly = true)
    public Optional<CoachTurnEntity> findTurn(UUID turnId) {
        return turnRepository.findById(turnId);
    }

    /** The names of the tools a turn called, in order. */
    @Transactional(readOnly = true)
    public List<String> toolNames(UUID turnId) {
        return toolRepository.findByTurnIdOrderBySeqAsc(turnId).stream()
                .map(ToolInvocationEntity::getTool)
                .toList();
    }

    @Transactional
    public void archiveThread(UUID userId, UUID threadId) {
        CoachThreadEntity thread = requireOwnedThread(userId, threadId);
        thread.setArchived(true);
        threadRepository.save(thread);
    }

    // ---------------------------------------------------------------------------------------

    private CoachThreadEntity requireOwnedThread(UUID userId, UUID threadId) {
        return threadRepository.findByThreadIdAndUserId(threadId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Coach thread not found"));
    }

    /**
     * A thread names itself from its opening question. Cut on a word boundary where one is
     * available, so a title reads as a phrase rather than a truncation.
     */
    private String deriveTitle(String firstMessage) {
        String cleaned = firstMessage.strip().replaceAll("\\s+", " ");
        if (cleaned.length() <= DERIVED_TITLE_LENGTH) {
            return cleaned;
        }
        String cut = cleaned.substring(0, DERIVED_TITLE_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        return (lastSpace > DERIVED_TITLE_LENGTH / 2 ? cut.substring(0, lastSpace) : cut) + "…";
    }


    private static String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }

    private CoachThreadView toThreadView(CoachThreadEntity entity, int turnCount) {
        return new CoachThreadView(
                entity.getThreadId(),
                entity.getTitle(),
                turnCount,
                entity.getLastActiveAt(),
                entity.getDateTimeCreated(),
                entity.getSurface(),
                entity.getLastActiveAt());
    }

    private CoachTurnView toTurnView(CoachTurnEntity turn, List<ToolInvocationEntity> tools) {
        return new CoachTurnView(
                turn.getTurnId(),
                turn.getSeq(),
                turn.getRole().name(),
                turn.getContent(),
                turn.getTier(),
                turn.getModel(),
                turn.getPromptTokens(),
                turn.getCompletionTokens(),
                turn.getLatencyMs(),
                turn.getGuardrailVerdict(),
                tools.stream().map(this::toToolView).toList(),
                turn.getDateTimeCreated());
    }

    private ToolInvocationView toToolView(ToolInvocationEntity invocation) {
        return new ToolInvocationView(
                invocation.getSeq(),
                invocation.getTool(),
                invocation.getArgsJson(),
                invocation.getResultSummary(),
                invocation.getLatencyMs(),
                invocation.isOk(),
                invocation.getError());
    }
}
