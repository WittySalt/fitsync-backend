package com.fitsync.recommendation.coach.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A copy of everything the coach holds about one person, for the account data export.
 *
 * <p>Only what the person wrote or was shown is included. The model telemetry on each answer, the
 * raw tool observations the orchestrator keeps for replay, the facts behind a reading and the
 * context row built from other services' events are left out, because they are either internal
 * bookkeeping or a copy of data the owning service exports itself.
 *
 * @param service         the service this part of the export came from
 * @param exportedAt      when the export was assembled
 * @param threads         every conversation, archived ones included, oldest first
 * @param sessionDrafts   the sessions the coach proposed, newest first
 * @param readings        the page readings and weekly reviews the coach wrote
 * @param recommendations the stored recommendation results
 */
@Schema(description = "Everything the coach holds about the caller, for the account data export.")
public record CoachDataExport(
        String service,
        Instant exportedAt,
        List<ThreadExport> threads,
        List<SessionDraftView> sessionDrafts,
        List<ReadingExport> readings,
        List<RecommendationExport> recommendations) {

    /** The name each part of the combined export is labelled with. */
    public static final String SERVICE_NAME = "recommendation-service";

    public CoachDataExport {
        threads = threads == null ? List.of() : List.copyOf(threads);
        sessionDrafts = sessionDrafts == null ? List.of() : List.copyOf(sessionDrafts);
        readings = readings == null ? List.of() : List.copyOf(readings);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    @Override
    public List<ThreadExport> threads() {
        return List.copyOf(threads);
    }

    @Override
    public List<SessionDraftView> sessionDrafts() {
        return List.copyOf(sessionDrafts);
    }

    @Override
    public List<ReadingExport> readings() {
        return List.copyOf(readings);
    }

    @Override
    public List<RecommendationExport> recommendations() {
        return List.copyOf(recommendations);
    }

    /**
     * One conversation with its turns in the order they were written.
     *
     * @param archived true when the person tidied the thread out of their list
     * @param surface  the page the thread was started from, or null
     */
    @Schema(description = "One conversation with the coach, with its turns in order.")
    public record ThreadExport(
            UUID threadId,
            String title,
            String surface,
            boolean archived,
            Instant createdAt,
            Instant lastActiveAt,
            List<TurnExport> turns) {

        public ThreadExport {
            turns = turns == null ? List.of() : List.copyOf(turns);
        }

        @Override
        public List<TurnExport> turns() {
            return List.copyOf(turns);
        }
    }

    /**
     * One message in a thread.
     *
     * @param seq  the position in the thread; numbers can skip where internal tool observations
     *             were left out
     * @param role USER for what the person wrote, ASSISTANT for the coach's reply
     */
    @Schema(description = "One message in a coach thread: the person's question or the coach's reply.")
    public record TurnExport(UUID turnId, int seq, String role, String content, Instant createdAt) {
    }

    /**
     * One reading the coach wrote for a page, or one weekly review (surface PROGRESS_WEEK).
     *
     * @param periodKey the Monday of the week for a weekly review, empty for a page reading
     */
    @Schema(description = "A page reading or weekly review the coach wrote.")
    public record ReadingExport(
            String surface,
            String periodKey,
            String outcome,
            String headline,
            String body,
            Instant generatedAt) {
    }

    /**
     * One stored recommendation result.
     *
     * @param payload the recommendation as it was stored, a JSON document in text form
     */
    @Schema(description = "A stored recommendation result.")
    public record RecommendationExport(String type, String source, String payload, Instant generatedAt) {
    }
}
