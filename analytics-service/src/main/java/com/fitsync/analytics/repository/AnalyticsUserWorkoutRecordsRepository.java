package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserWorkoutRecordsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserWorkoutRecordsRepository
        extends JpaRepository<AnalyticsUserWorkoutRecordsEntity, UUID> {
}
