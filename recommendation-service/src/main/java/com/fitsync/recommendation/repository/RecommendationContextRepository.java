package com.fitsync.recommendation.repository;

import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationContextRepository extends JpaRepository<RecommendationContextEntity, UUID> {
}
