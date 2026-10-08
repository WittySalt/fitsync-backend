package com.fitsync.notification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitsync.notification.entity.RecipientEntity;

public interface RecipientRepository extends JpaRepository<RecipientEntity, UUID> {

    List<RecipientEntity> findByEmailEnabledTrueAndWorkoutReminderEnabledTrue();
}
