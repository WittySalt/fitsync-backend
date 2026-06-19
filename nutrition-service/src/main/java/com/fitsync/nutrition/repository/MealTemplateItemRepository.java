package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.MealTemplateItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealTemplateItemRepository extends JpaRepository<MealTemplateItemEntity, UUID> {

    List<MealTemplateItemEntity> findByTemplateIdOrderBySortOrderAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
