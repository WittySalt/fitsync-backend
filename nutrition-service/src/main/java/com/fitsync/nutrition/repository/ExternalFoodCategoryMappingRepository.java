package com.fitsync.nutrition.repository;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.ExternalFoodCategoryMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalFoodCategoryMappingRepository
        extends JpaRepository<ExternalFoodCategoryMappingEntity, UUID> {

    Optional<ExternalFoodCategoryMappingEntity> findBySourceDatasetAndSourceCategoryCode(
            String sourceDataset, String sourceCategoryCode);
}
