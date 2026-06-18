package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsUserDashboardSnapshotRepository
        extends JpaRepository<AnalyticsUserDashboardSnapshotEntity, UUID> {

    @Query("select snapshot.userId from AnalyticsUserDashboardSnapshotEntity snapshot order by snapshot.userId")
    Slice<UUID> findSnapshotUserIds(Pageable pageable);
}
