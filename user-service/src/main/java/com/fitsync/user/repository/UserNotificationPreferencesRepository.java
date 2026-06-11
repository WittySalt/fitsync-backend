package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferencesEntity, UUID> {
}
