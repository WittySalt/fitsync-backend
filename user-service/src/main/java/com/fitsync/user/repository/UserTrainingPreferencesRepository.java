package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTrainingPreferencesRepository extends JpaRepository<UserTrainingPreferencesEntity, UUID> {
}
