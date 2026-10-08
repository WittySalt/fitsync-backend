package com.fitsync.notification.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitsync.notification.entity.WeightEntity;

public interface WeightRepository extends JpaRepository<WeightEntity, UUID> {

    Optional<WeightEntity> findFirstByUserIdOrderByMeasuredAtDesc(UUID userId);

    Optional<WeightEntity> findFirstByUserIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(UUID userId, Instant before);

    Optional<WeightEntity> findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(UUID userId, Instant at);

    Optional<WeightEntity> findFirstByUserIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDesc(
            UUID userId, Instant from, Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WeightEntity w where w.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
