package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.CoachThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachThreadRepository extends JpaRepository<CoachThreadEntity, UUID> {

    /** The thread list a user sees: theirs, still live, most recently used first. */
    List<CoachThreadEntity> findByUserIdAndArchivedFalseOrderByLastActiveAtDesc(UUID userId);

    /** Ownership is checked by querying for it rather than by loading and comparing afterwards. */
    Optional<CoachThreadEntity> findByThreadIdAndUserId(UUID threadId, UUID userId);

    void deleteByUserId(UUID userId);
}
