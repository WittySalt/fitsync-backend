package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.MealTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealTemplateRepository extends JpaRepository<MealTemplateEntity, UUID> {

    List<MealTemplateEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<MealTemplateEntity> findByTemplateIdAndUserId(UUID templateId, UUID userId);
}
