package com.fitsync.nutrition.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.WaterLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterLogRepository extends JpaRepository<WaterLogEntity, UUID> {

    List<WaterLogEntity> findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThanEqualOrderByLoggedAtAsc(
            UUID userId, Instant from, Instant to);

    Optional<WaterLogEntity> findByWaterIdAndUserId(UUID waterId, UUID userId);

    void deleteByUserId(UUID userId);
}
