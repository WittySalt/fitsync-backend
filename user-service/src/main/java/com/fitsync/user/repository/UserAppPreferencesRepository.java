package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserAppPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAppPreferencesRepository extends JpaRepository<UserAppPreferencesEntity, UUID> {
}
