package com.fitsync.recommendation.repository;

import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationProcessedEventRepository
        extends JpaRepository<RecommendationProcessedEventEntity, UUID> {

    void deleteByUserId(UUID userId);
}
