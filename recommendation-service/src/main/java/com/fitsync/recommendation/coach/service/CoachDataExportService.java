package com.fitsync.recommendation.coach.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachDataExport;
import com.fitsync.recommendation.coach.dto.CoachDataExport.ReadingExport;
import com.fitsync.recommendation.coach.dto.CoachDataExport.RecommendationExport;
import com.fitsync.recommendation.coach.dto.CoachDataExport.ThreadExport;
import com.fitsync.recommendation.coach.dto.CoachDataExport.TurnExport;
import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.reading.CoachPageReadingEntity;
import com.fitsync.recommendation.coach.reading.CoachPageReadingRepository;
import com.fitsync.recommendation.coach.repository.CoachThreadRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.entity.RecommendationResultEntity;
import com.fitsync.recommendation.repository.RecommendationResultRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the coach part of a person's account data export.
 *
 * <p>The inventory follows the account-erasure purge ({@code CoachConversationPurger},
 * {@code CoachPageReadingPurger} and {@code AccountDeletedEventHandler}), so whatever erasure removes
 * as the person's own data is also what they can take a copy of. Two things erasure removes are
 * deliberately not exported: the recommendation context row, which is a projection of other
 * services' data that those services export themselves, and the processed-event ledger, which is
 * delivery bookkeeping rather than anything about the person.
 *
 * <p>Every read is scoped to the caller's user id. A turn has no user id of its own, so turns are
 * only ever read through a thread that was first found by owner.
 *
 * <p>The repositories are held through {@link ObjectProvider} and resolved at use time. Each Spring
 * Data repository declares save and delete methods, which SpotBugs would otherwise read as a mutable
 * collaborator stored from the constructor.
 */
@Service
public class CoachDataExportService {

    private final ObjectProvider<CoachThreadRepository> threadRepository;
    private final ObjectProvider<CoachTurnRepository> turnRepository;
    private final ObjectProvider<SessionDraftService> sessionDraftService;
    private final ObjectProvider<CoachPageReadingRepository> readingRepository;
    private final ObjectProvider<RecommendationResultRepository> resultRepository;
    private final Clock clock;

    public CoachDataExportService(ObjectProvider<CoachThreadRepository> threadRepository,
                                  ObjectProvider<CoachTurnRepository> turnRepository,
                                  ObjectProvider<SessionDraftService> sessionDraftService,
                                  ObjectProvider<CoachPageReadingRepository> readingRepository,
                                  ObjectProvider<RecommendationResultRepository> resultRepository,
                                  Clock clock) {
        this.threadRepository = threadRepository;
        this.turnRepository = turnRepository;
        this.sessionDraftService = sessionDraftService;
        this.readingRepository = readingRepository;
        this.resultRepository = resultRepository;
        this.clock = clock;
    }

    /**
     * Everything the coach holds about one person. An account with no coach data gets empty lists.
     */
    @Transactional(readOnly = true)
    public CoachDataExport export(UUID userId) {
        return new CoachDataExport(
                CoachDataExport.SERVICE_NAME,
                Instant.now(clock),
                threads(userId),
                sessionDraftService.getObject().list(userId),
                readings(userId),
                recommendations(userId));
    }

    private List<ThreadExport> threads(UUID userId) {
        CoachTurnRepository turns = turnRepository.getObject();
        return threadRepository.getObject()
                .findByUserIdOrderByDateTimeCreatedAscThreadIdAsc(userId)
                .stream()
                .map(thread -> toThreadExport(thread, turns.findByThreadIdOrderBySeqAsc(thread.getThreadId())))
                .toList();
    }

    private static ThreadExport toThreadExport(CoachThreadEntity thread, List<CoachTurnEntity> turns) {
        // TOOL turns are observations the orchestrator appended for replay. They were never shown to
        // the person as a message and can hold raw tool output, so only the two speakers are kept.
        List<TurnExport> messages = turns.stream()
                .filter(turn -> turn.getRole() != CoachTurnRole.TOOL)
                .map(turn -> new TurnExport(turn.getTurnId(), turn.getSeq(), turn.getRole().name(),
                        turn.getContent(), turn.getDateTimeCreated()))
                .toList();
        return new ThreadExport(thread.getThreadId(), thread.getTitle(), thread.getSurface(),
                thread.isArchived(), thread.getDateTimeCreated(), thread.getLastActiveAt(), messages);
    }

    private List<ReadingExport> readings(UUID userId) {
        return readingRepository.getObject()
                .findByUserIdOrderBySurfaceAscPeriodKeyAsc(userId)
                .stream()
                .map(CoachDataExportService::toReadingExport)
                .toList();
    }

    private static ReadingExport toReadingExport(CoachPageReadingEntity reading) {
        return new ReadingExport(
                reading.getSurface() == null ? null : reading.getSurface().name(),
                reading.getPeriodKey(),
                reading.getOutcome() == null ? null : reading.getOutcome().name(),
                reading.getHeadline(),
                reading.getBody(),
                reading.getGeneratedAt());
    }

    private List<RecommendationExport> recommendations(UUID userId) {
        return resultRepository.getObject()
                .findByUserId(userId)
                .stream()
                .map(CoachDataExportService::toRecommendationExport)
                .toList();
    }

    private static RecommendationExport toRecommendationExport(RecommendationResultEntity result) {
        return new RecommendationExport(
                result.getType(),
                result.getSource() == null ? null : result.getSource().name(),
                result.getPayload(),
                result.getGeneratedAt());
    }
}
