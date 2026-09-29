package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.ToolInvocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolInvocationRepository extends JpaRepository<ToolInvocationEntity, UUID> {

    List<ToolInvocationEntity> findByTurnIdOrderBySeqAsc(UUID turnId);

    List<ToolInvocationEntity> findByTurnIdInOrderBySeqAsc(List<UUID> turnIds);

    /**
     * Removes every tool invocation recorded against one of a user's turns, in a single statement.
     * Neither a tool invocation nor its turn carries a {@code user_id}; ownership is reached through
     * two hops, turn to thread to user, the same chain the rest of the coach layer relies on.
     *
     * <p>Call this before {@link CoachTurnRepository#deleteByUserId} and
     * {@link CoachThreadRepository#deleteByUserId}, since its subquery needs the turn and thread rows
     * to still exist.
     *
     * @return how many tool invocations were removed, for the account-deletion audit log.
     */
    @Modifying
    @Query("delete from ToolInvocationEntity ti where ti.turnId in "
            + "(select t.turnId from CoachTurnEntity t where t.threadId in "
            + "(select th.threadId from CoachThreadEntity th where th.userId = :userId))")
    int deleteByUserId(@Param("userId") UUID userId);
}
