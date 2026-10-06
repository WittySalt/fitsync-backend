package com.fitsync.recommendation.coach.service;

import java.util.UUID;

import com.fitsync.recommendation.coach.repository.CoachThreadRepository;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.recommendation.coach.repository.ToolInvocationRepository;
import com.fitsync.recommendation.handler.AccountDataPurger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Removes a deleted user's own coach data: their conversation threads, the turns in them, the tool
 * calls recorded against those turns, and the artifacts (session drafts) the coach generated for
 * them. This is what {@link com.fitsync.recommendation.coach.reading.CoachPageReadingPurger} does
 * not cover — that class only clears the cached page reading, not the conversation itself, and a
 * thread's turns hold what the user typed and what the coach said about their training, which is
 * personal data like any other.
 *
 * <p>Implements {@code AccountDataPurger} rather than being called directly from
 * {@code AccountDeletedEventHandler}, for the same reason {@code CoachPageReadingPurger} does: that
 * handler lives in the projection and must never import the coach package (see
 * {@code CoachDependencyDirectionTests}). This class is the coach side of that boundary.
 *
 * <p>The four deletes are kept in one purger, in a fixed order, rather than split across several
 * beans, because the order is not incidental. A turn and a tool invocation carry no {@code user_id}
 * of their own — ownership is reached through the thread they belong to — so their bulk deletes are
 * written as subqueries against {@code coach_thread}, which means those subqueries need the thread
 * rows to still exist. Deleting threads first would leave nothing for the child deletes to match.
 * The order here is therefore: tool invocations, then turns, then threads, then artifacts (which
 * carry their own {@code user_id} and have no such dependency, since their thread reference is
 * {@code on delete set null} rather than cascading — see
 * {@code V7__create_generated_artifact_table.sql}). Spring hands every {@link AccountDataPurger} to
 * the handler as a plain list with no guaranteed ordering between beans; keeping the whole sequence
 * inside one purger means that guarantee is never needed.
 */
@Component
class CoachConversationPurger implements AccountDataPurger {

    private static final Logger LOG = LoggerFactory.getLogger(CoachConversationPurger.class);

    private final ToolInvocationRepository toolInvocationRepository;
    private final CoachTurnRepository turnRepository;
    private final CoachThreadRepository threadRepository;
    private final GeneratedArtifactRepository artifactRepository;

    CoachConversationPurger(ToolInvocationRepository toolInvocationRepository,
                            CoachTurnRepository turnRepository,
                            CoachThreadRepository threadRepository,
                            GeneratedArtifactRepository artifactRepository) {
        this.toolInvocationRepository = toolInvocationRepository;
        this.turnRepository = turnRepository;
        this.threadRepository = threadRepository;
        this.artifactRepository = artifactRepository;
    }

    @Override
    public void purgeForUser(UUID userId) {
        int toolInvocationsRemoved = toolInvocationRepository.deleteByUserId(userId);
        int turnsRemoved = turnRepository.deleteByUserId(userId);
        int threadsRemoved = threadRepository.deleteByUserId(userId);
        int artifactsRemoved = artifactRepository.deleteByUserId(userId);

        // Only the fact that a purge ran and how many rows each table lost is logged. Nothing about
        // what the user typed or what the coach said is ever written here. The user id is sanitised
        // against CRLF the same way the rest of this service sanitises log values.
        LOG.info("event=coach_account_data_purged userId={} threads={} turns={} toolInvocations={} artifacts={}",
                sanitizeForLog(userId), threadsRemoved, turnsRemoved, toolInvocationsRemoved, artifactsRemoved);
    }

    private static String sanitizeForLog(UUID userId) {
        return String.valueOf(userId).replace("\r", "_").replace("\n", "_");
    }
}
