package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("select coalesce(max(t.seq), 0) from CoachTurnEntity t where t.threadId = :threadId")
    int findMaxSeq(@Param("threadId") UUID threadId);
}
