package com.fitsync.user.repository.code;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.code.CodeValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeValueRepository extends JpaRepository<CodeValueEntity, UUID> {

    Optional<CodeValueEntity> findByCodeSetKeyAndCode(String codeSetKey, String code);

    List<CodeValueEntity> findByCodeSetKeyAndActiveTrueOrderBySortOrderAsc(String codeSetKey);

    List<CodeValueEntity> findByCodeSetKeyAndActiveTrueAndDateEffectiveFromLessThanEqualAndDateEffectiveToGreaterThanEqualOrderBySortOrderAsc(
            String codeSetKey, LocalDate effectiveFrom, LocalDate effectiveTo);
}
