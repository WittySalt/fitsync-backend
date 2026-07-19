package com.fitsync.analytics.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_user_workout_records")
public class AnalyticsUserWorkoutRecordsEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "personal_record_count", nullable = false)
    private Integer personalRecordCount;

    @Column(name = "first_record_at")
    private Instant firstRecordAt;

    @Column(name = "last_record_at")
    private Instant lastRecordAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getPersonalRecordCount() {
        return personalRecordCount;
    }

    public void setPersonalRecordCount(Integer personalRecordCount) {
        this.personalRecordCount = personalRecordCount;
    }

    public Instant getFirstRecordAt() {
        return firstRecordAt;
    }

    public void setFirstRecordAt(Instant firstRecordAt) {
        this.firstRecordAt = firstRecordAt;
    }

    public Instant getLastRecordAt() {
        return lastRecordAt;
    }

    public void setLastRecordAt(Instant lastRecordAt) {
        this.lastRecordAt = lastRecordAt;
    }
}
