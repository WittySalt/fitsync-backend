package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.NutrientDailyReferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NutrientDailyReferenceRepository extends JpaRepository<NutrientDailyReferenceEntity, UUID> {

    /** Every row that applies to the age, for the person's own sex and for ANY, so the caller picks the best match. */
    @Query("""
            select r
            from NutrientDailyReferenceEntity r
            where r.sex in (:sex, 'ANY')
              and r.ageFrom <= :age
              and r.ageTo >= :age
            order by r.nutrientCode asc, r.kind asc
            """)
    List<NutrientDailyReferenceEntity> findApplicable(@Param("sex") String sex, @Param("age") int age);

    /** Every row of one nutrient, for the table of all age bands. */
    List<NutrientDailyReferenceEntity> findByNutrientCodeOrderByAgeFromAscSexAsc(String nutrientCode);
}
