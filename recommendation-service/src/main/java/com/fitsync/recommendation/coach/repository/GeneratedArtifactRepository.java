package com.fitsync.recommendation.coach.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.GeneratedArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifactEntity, UUID> {

    /**
     * Ownership is part of the query rather than checked afterwards, so another user's artifact is
     * indistinguishable from one that does not exist.
     */
    Optional<GeneratedArtifactEntity> findByArtifactIdAndUserId(UUID artifactId, UUID userId);

    /**
     * Removes every artifact a user generated, in a single statement. An artifact outlives the
     * thread it came from (its {@code thread_id} foreign key is {@code on delete set null}, not
     * cascade — see {@code V7__create_generated_artifact_table.sql}), so it is never cleaned up as a
     * side effect of deleting the user's threads and needs this delete of its own.
     *
     * @return how many artifacts were removed, for the account-deletion audit log.
     */
    @Modifying
    @Query("delete from GeneratedArtifactEntity a where a.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

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
