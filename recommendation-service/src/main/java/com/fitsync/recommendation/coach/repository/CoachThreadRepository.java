package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachThreadRepository extends JpaRepository<CoachThreadEntity, UUID> {

    /** The thread list a user sees: theirs, still live, most recently used first. */
    List<CoachThreadEntity> findByUserIdAndArchivedFalseOrderByLastActiveAtDesc(UUID userId);

    /** One page of the thread list; the caller supplies the sort so the order is stated where it is used. */
    Page<CoachThreadEntity> findByUserIdAndArchivedFalse(UUID userId, Pageable pageable);

    /** Ownership is checked by querying for it rather than by loading and comparing afterwards. */
    Optional<CoachThreadEntity> findByThreadIdAndUserId(UUID threadId, UUID userId);

    /**
     * Removes every thread a user owns, archived or not, in a single statement rather than loading
     * and removing each one. {@code coach_turn} and {@code tool_invocation} both cascade from this
     * table at the database level, but the caller deletes them explicitly first anyway, so that each
     * table's row count is known for the account-deletion audit log rather than folded into one
     * cascaded number.
     *
     * @return how many threads were removed, for the account-deletion audit log.
     */
    @Modifying
    @Query("delete from CoachThreadEntity t where t.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
