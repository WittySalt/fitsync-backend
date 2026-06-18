package com.fitsync.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.AccountDataExportRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDataExportRequestRepository extends JpaRepository<AccountDataExportRequestEntity, UUID> {

    Optional<AccountDataExportRequestEntity> findTopByUserIdOrderByRequestedAtDesc(UUID userId);

    Optional<AccountDataExportRequestEntity> findByExportRequestIdAndUserId(UUID exportRequestId, UUID userId);
}
