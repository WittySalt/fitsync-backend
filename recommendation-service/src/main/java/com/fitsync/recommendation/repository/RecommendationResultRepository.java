package com.fitsync.recommendation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationResultRepository extends JpaRepository<RecommendationResultEntity, UUID> {

    List<RecommendationResultEntity> findByUserId(UUID userId);

    Optional<RecommendationResultEntity> findByUserIdAndType(UUID userId, String type);

    void deleteByUserId(UUID userId);
}
