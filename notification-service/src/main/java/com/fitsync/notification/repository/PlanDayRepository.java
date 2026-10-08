package com.fitsync.notification.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitsync.notification.entity.PlanDayEntity;

public interface PlanDayRepository extends JpaRepository<PlanDayEntity, PlanDayEntity.Key> {

    Optional<PlanDayEntity> findByUserIdAndDayOfWeek(UUID userId, int dayOfWeek);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlanDayEntity d where d.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
