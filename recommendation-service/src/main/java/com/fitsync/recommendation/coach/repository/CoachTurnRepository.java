package com.fitsync.recommendation.coach.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachTurnRepository extends JpaRepository<CoachTurnEntity, UUID> {

    List<CoachTurnEntity> findByThreadIdOrderBySeqAsc(UUID threadId);

    /**
     * The context window: the tail of the thread, newest first so a limit can be applied, and
     * reversed by the caller. Ordering on seq rather than time means two turns written in the same
     * millisecond still order correctly.
     */
    List<CoachTurnEntity> findByThreadIdOrderBySeqDesc(UUID threadId);

    /** The user turn a retried question refers to, by the id the client gave it. */
    Optional<CoachTurnEntity> findByThreadIdAndClientTurnId(UUID threadId, UUID clientTurnId);

    Optional<CoachTurnEntity> findByThreadIdAndSeq(UUID threadId, Integer seq);

    /**
     * When a user's questions were asked since a moment, oldest first, across all of their threads
     * (archived or not, since archiving must not reset a limit). Only the timestamps are read.
     */
    @Query("select t.dateTimeCreated from CoachTurnEntity t, CoachThreadEntity th "
            + "where t.threadId = th.threadId and th.userId = :userId and t.role = :role "
            + "and t.dateTimeCreated >= :since order by t.dateTimeCreated asc")
    List<Instant> findTurnTimesSince(@Param("userId") UUID userId,
                                     @Param("role") CoachTurnRole role,
                                     @Param("since") Instant since);

    @Query("select coalesce(max(t.seq), 0) from CoachTurnEntity t where t.threadId = :threadId")
    int findMaxSeq(@Param("threadId") UUID threadId);

    /**
     * Removes every turn belonging to one of a user's threads, in a single statement. A turn carries
     * no {@code user_id} of its own, so ownership is reached through its thread, the same way the
     * rest of the coach layer scopes a turn to a user.
     *
     * <p>Call this only after {@link com.fitsync.recommendation.coach.repository.ToolInvocationRepository#deleteByUserId}
     * has removed the tool invocations that reference these turns, and before the threads themselves
     * are deleted, so every delete in the sequence still has the parent rows its subquery needs.
     *
     * @return how many turns were removed, for the account-deletion audit log.
     */
    @Modifying
    @Query("delete from CoachTurnEntity t where t.threadId in "
            + "(select th.threadId from CoachThreadEntity th where th.userId = :userId)")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * The columns the admin quality report aggregates, for turns of one role inside one window. A
     * constructor projection rather than the entity, so {@code content} is never loaded — see
     * {@link CoachTurnQualityRow}.
     */
    @Query("select new com.fitsync.recommendation.coach.repository.CoachTurnQualityRow("
            + "t.tier, t.model, t.latencyMs, t.guardrailVerdict, t.dateTimeCreated) "
            + "from CoachTurnEntity t "
            + "where t.role = :role and t.dateTimeCreated >= :start and t.dateTimeCreated < :end")
    List<CoachTurnQualityRow> findQualityRows(@Param("role") CoachTurnRole role,
                                              @Param("start") Instant start,
                                              @Param("end") Instant end);
}
