package com.fitsync.recommendation.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.ArtifactStatus;
import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.entity.DraftSource;
import com.fitsync.recommendation.coach.entity.GeneratedArtifactEntity;
import com.fitsync.recommendation.coach.entity.ToolInvocationEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingRepository;
import com.fitsync.recommendation.coach.reading.ReadingOutcome;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
import com.fitsync.recommendation.coach.repository.CoachThreadRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.recommendation.coach.repository.ToolInvocationRepository;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.entity.RecommendationProcessedEventEntity;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.entity.RecommendationSource;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.repository.RecommendationProcessedEventRepository;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards against a new user-owned table being added without anyone deciding what happens to it on
 * account deletion.
 *
 * <p>{@link #everyMetamodelEntityHasADecidedFate()} reads every entity Spring knows about off the
 * JPA metamodel rather than off a hand-written list, so a new {@code @Entity} class shows up here
 * automatically. If nobody has added it to {@link #COVERED_ENTITIES} below, that test fails and
 * names the entity — the build will not pass until someone looks at it and either purges it or
 * records why it is exempt.
 *
 * <p>{@link #deletingAnAccountRemovesEveryUserOwnedRowAndLeavesAnotherUsersRowsAlone()} is the
 * behavioural half: it creates a row owned by user A and a row owned by user B in every entity
 * currently known to belong to a user, deletes A's account, and checks that everything reachable
 * from A is gone from every one of those entities while B's rows are untouched. An entity belongs
 * to a user either directly (a {@code userId} column) or by association (a turn through its thread,
 * a tool invocation through its turn and thread).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountDeletionCoverageTests {

    /**
     * Every entity in the module, and the decision made for it on account deletion. When the
     * metamodel contains a class not listed here, {@link #everyMetamodelEntityHasADecidedFate()}
     * fails and names it: a new table cannot be added without that decision being made.
     */
    private static final Set<Class<?>> COVERED_ENTITIES = Set.of(
            RecommendationContextEntity.class,
            RecommendationResultEntity.class,
            RecommendationProcessedEventEntity.class,
            CoachPageReadingEntity.class,
            CoachThreadEntity.class,
            CoachTurnEntity.class,
            ToolInvocationEntity.class,
            GeneratedArtifactEntity.class);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AccountDeletedEventHandler accountDeletedEventHandler;

    @Autowired
    private RecommendationContextRepository contextRepository;

    @Autowired
    private RecommendationResultRepository resultRepository;

    @Autowired
    private RecommendationProcessedEventRepository processedEventRepository;

    @Autowired
    private CoachPageReadingRepository readingRepository;

    @Autowired
    private CoachThreadRepository threadRepository;

    @Autowired
    private CoachTurnRepository turnRepository;

    @Autowired
    private ToolInvocationRepository toolInvocationRepository;

    @Autowired
    private GeneratedArtifactRepository artifactRepository;

    @Test
    void everyMetamodelEntityHasADecidedFate() {
        Set<Class<?>> entitiesInTheService = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .collect(Collectors.toSet());

        Set<Class<?>> undecided = new HashSet<>(entitiesInTheService);
        undecided.removeAll(COVERED_ENTITIES);

        assertThat(undecided)
                .withFailMessage(() -> "Account deletion coverage must be decided for: "
                        + undecided.stream().map(Class::getName).collect(Collectors.joining(", "))
                        + ". Add it to AccountDeletionCoverageTests.COVERED_ENTITIES together with"
                        + " either a purger and a factory here, or a documented reason it needs none.")
                .isEmpty();
    }

    @Test
    void deletingAnAccountRemovesEveryUserOwnedRowAndLeavesAnotherUsersRowsAlone() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        createContext(userA);
        createContext(userB);

        createResult(userA);
        createResult(userB);

        // A row already on the ledger before the deletion, unrelated to the AccountDeleted event
        // itself, so the test can tell "the old rows were purged" apart from "the new one arrived".
        createProcessedEvent(userA);
        UUID priorEventForB = createProcessedEvent(userB);

        createReading(userA);
        createReading(userB);

        UUID activeThreadA = createThread(userA, false);
        UUID archivedThreadA = createThread(userA, true);
        UUID turnA = createTurn(activeThreadA);
        createToolInvocation(turnA);
        createArtifact(userA, activeThreadA);

        UUID threadB = createThread(userB, false);
        UUID turnB = createTurn(threadB);
        createToolInvocation(turnB);
        createArtifact(userB, threadB);

        entityManager.flush();

        FitSyncDomainEvent<AccountDeletedPayload> deletion = accountDeletedEvent(userA);
        accountDeletedEventHandler.consume(deletion);
        entityManager.flush();
        entityManager.clear();

        // Every entity user A appears in, directly or through a thread or turn, is now empty for A.
        assertThat(contextRepository.findById(userA)).isEmpty();
        assertThat(resultRepository.findByUserId(userA)).isEmpty();
        assertThat(readingRepository.findByUserIdAndSurface(userA, ReadingSurface.WORKOUT)).isEmpty();
        assertThat(threadRepository.findByThreadIdAndUserId(activeThreadA, userA)).isEmpty();
        assertThat(threadRepository.findByThreadIdAndUserId(archivedThreadA, userA))
                .withFailMessage("account deletion must remove archived threads too, not just live ones")
                .isEmpty();
        assertThat(turnRepository.findByThreadIdOrderBySeqAsc(activeThreadA)).isEmpty();
        assertThat(toolInvocationRepository.findByTurnIdOrderBySeqAsc(turnA)).isEmpty();
        assertThat(artifactRepository.findByUserIdAndKindOrderByDateTimeCreatedDesc(userA, ArtifactKind.SESSION_DRAFT))
                .isEmpty();

        // The one intended exception: the processed-event ledger keeps the AccountDeleted event
        // itself, because ProcessedEventGuard relies on that row for idempotency (see change 2,
        // step 5, of the task this test verifies). Every other row the ledger held for A — including
        // the one seeded above — is gone.
        List<RecommendationProcessedEventEntity> ledgerRowsForA = processedEventRepository.findAll().stream()
                .filter(row -> userA.equals(row.getUserId()))
                .toList();
        assertThat(ledgerRowsForA)
                .singleElement()
                .satisfies(row -> assertThat(row.getEventId()).isEqualTo(deletion.eventId()));

        // User B's rows, in every one of the same entities, are untouched.
        assertThat(contextRepository.findById(userB)).isPresent();
        assertThat(resultRepository.findByUserId(userB)).hasSize(1);
        assertThat(readingRepository.findByUserIdAndSurface(userB, ReadingSurface.WORKOUT)).isPresent();
        assertThat(threadRepository.findByThreadIdAndUserId(threadB, userB)).isPresent();
        assertThat(turnRepository.findByThreadIdOrderBySeqAsc(threadB)).hasSize(1);
        assertThat(toolInvocationRepository.findByTurnIdOrderBySeqAsc(turnB)).hasSize(1);
        assertThat(artifactRepository.findByUserIdAndKindOrderByDateTimeCreatedDesc(userB, ArtifactKind.SESSION_DRAFT))
                .hasSize(1);
        assertThat(processedEventRepository.findById(priorEventForB)).isPresent();

        // Handling the same AccountDeleted event a second time changes nothing and does not fail:
        // Kafka delivery is at-least-once, so this is the ordinary case, not an edge case.
        accountDeletedEventHandler.consume(deletion);
        entityManager.flush();

        assertThat(processedEventRepository.findAll().stream()
                .filter(row -> userA.equals(row.getUserId()))
                .toList())
                .singleElement()
                .satisfies(row -> assertThat(row.getEventId()).isEqualTo(deletion.eventId()));
    }

    // --- per-entity factories -----------------------------------------------------------------

    private void createContext(UUID userId) {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(userId);
        contextRepository.save(context);
    }

    private void createResult(UUID userId) {
        RecommendationResultEntity result = new RecommendationResultEntity();
        result.setId(UUID.randomUUID());
        result.setUserId(userId);
        result.setType("NUTRITION_GAP");
        result.setSource(RecommendationSource.RULE);
        result.setPayload("{}");
        result.setGeneratedAt(Instant.parse("2026-07-06T09:00:00Z"));
        resultRepository.save(result);
    }

    private UUID createProcessedEvent(UUID userId) {
        RecommendationProcessedEventEntity processedEvent = new RecommendationProcessedEventEntity();
        UUID eventId = UUID.randomUUID();
        processedEvent.setEventId(eventId);
        processedEvent.setEventType("UserRegistered");
        processedEvent.setUserId(userId);
        processedEvent.setProcessedAt(Instant.parse("2026-07-06T09:00:00Z"));
        processedEventRepository.save(processedEvent);
        return eventId;
    }

    private void createReading(UUID userId) {
        CoachPageReadingEntity reading = new CoachPageReadingEntity();
        reading.setId(UUID.randomUUID());
        reading.setUserId(userId);
        reading.setSurface(ReadingSurface.WORKOUT);
        reading.setFactsHash("hash");
        reading.setOutcome(ReadingOutcome.ACCEPTED);
        reading.setStartedAt(Instant.parse("2026-07-06T09:00:00Z"));
        readingRepository.save(reading);
    }

    private UUID createThread(UUID userId, boolean archived) {
        CoachThreadEntity thread = new CoachThreadEntity();
        UUID threadId = UUID.randomUUID();
        thread.setThreadId(threadId);
        thread.setUserId(userId);
        thread.setTitle("Coverage thread");
        thread.setArchived(archived);
        thread.setLastActiveAt(Instant.parse("2026-07-06T09:00:00Z"));
        threadRepository.save(thread);
        return threadId;
    }

    private UUID createTurn(UUID threadId) {
        CoachTurnEntity turn = new CoachTurnEntity();
        UUID turnId = UUID.randomUUID();
        turn.setTurnId(turnId);
        turn.setThreadId(threadId);
        turn.setSeq(1);
        turn.setRole(CoachTurnRole.USER);
        turn.setContent("Should I train today?");
        turnRepository.save(turn);
        return turnId;
    }

    private void createToolInvocation(UUID turnId) {
        ToolInvocationEntity invocation = new ToolInvocationEntity();
        invocation.setInvocationId(UUID.randomUUID());
        invocation.setTurnId(turnId);
        invocation.setSeq(1);
        invocation.setTool("readFacts");
        invocation.setLatencyMs(4);
        invocation.setOk(true);
        toolInvocationRepository.save(invocation);
    }

    private void createArtifact(UUID userId, UUID threadId) {
        GeneratedArtifactEntity artifact = new GeneratedArtifactEntity();
        artifact.setArtifactId(UUID.randomUUID());
        artifact.setUserId(userId);
        artifact.setThreadId(threadId);
        artifact.setKind(ArtifactKind.SESSION_DRAFT);
        artifact.setStatus(ArtifactStatus.DRAFT);
        artifact.setPayload("{}");
        artifact.setSource(DraftSource.MODEL);
        artifact.setValidationAttempts(1);
        artifactRepository.save(artifact);
    }

    private static FitSyncDomainEvent<AccountDeletedPayload> accountDeletedEvent(UUID userId) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(), "AccountDeleted", 1, Instant.parse("2026-07-06T12:00:00Z"),
                "test", userId, new AccountDeletedPayload(Instant.parse("2026-07-06T10:00:00Z"), "USER_REQUEST"));
    }
}
