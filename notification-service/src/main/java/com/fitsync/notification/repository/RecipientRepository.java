package com.fitsync.notification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitsync.notification.entity.RecipientEntity;

public interface RecipientRepository extends JpaRepository<RecipientEntity, UUID> {

    List<RecipientEntity> findByEmailEnabledTrueAndWorkoutReminderEnabledTrue();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecipientEntity r where r.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
