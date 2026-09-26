package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.GeneratedArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifactEntity, UUID> {

    /**
     * Ownership is part of the query rather than checked afterwards, so another user's artifact is
     * indistinguishable from one that does not exist.
     */
    Optional<GeneratedArtifactEntity> findByArtifactIdAndUserId(UUID artifactId, UUID userId);

    List<GeneratedArtifactEntity> findByUserIdAndKindOrderByDateTimeCreatedDesc(UUID userId, ArtifactKind kind);

    /**
     * How drafts of one kind were produced, counted by source and by how many validations each
     * needed.
     *
     * <p>Aggregated in the database rather than by loading the rows, because the harness will ask
     * this across every user once there are more than a handful of drafts. Returns one row per
     * source with its count and the number that passed on the first attempt.
     */
    @Query("""
            select artifact.source,
                   count(artifact),
                   sum(case when artifact.validationAttempts = 1 then 1 else 0 end)
              from GeneratedArtifactEntity artifact
             where artifact.kind = :kind
               and (:userId is null or artifact.userId = :userId)
             group by artifact.source
            """)
    List<Object[]> generationCounts(ArtifactKind kind, UUID userId);
}
