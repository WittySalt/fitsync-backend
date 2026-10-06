package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserCooksEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserCooksRepository extends JpaRepository<AnalyticsUserCooksEntity, UUID> {
}
