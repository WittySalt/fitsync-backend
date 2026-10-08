package com.fitsync.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitsync.notification.entity.GoalEntity;

public interface GoalRepository extends JpaRepository<GoalEntity, UUID> {

    List<GoalEntity> findByUserIdAndGoalTypeAndStatus(UUID userId, String goalType, String status);

    Optional<GoalEntity> findFirstByUserIdAndGoalTypeAndStatusOrderByUpdatedAtDesc(
            UUID userId, String goalType, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoalEntity g where g.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
