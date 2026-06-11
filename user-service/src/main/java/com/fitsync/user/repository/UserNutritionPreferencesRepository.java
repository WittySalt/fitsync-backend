package com.fitsync.user.repository;

import java.util.UUID;

import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNutritionPreferencesRepository extends JpaRepository<UserNutritionPreferencesEntity, UUID> {
}
