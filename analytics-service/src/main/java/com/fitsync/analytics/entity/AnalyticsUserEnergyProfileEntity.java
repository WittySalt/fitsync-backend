package com.fitsync.analytics.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One EnergyProfileComputed event, kept as history. The event id is the key, so a replay cannot add a second row. */
@Entity
@Table(name = "analytics_user_energy_profiles")
public class AnalyticsUserEnergyProfileEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "bmr_kcal", nullable = false)
    private Integer bmrKcal;

    @Column(name = "estimated", nullable = false)
    private boolean estimated;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getBmrKcal() {
        return bmrKcal;
    }

    public void setBmrKcal(Integer bmrKcal) {
        this.bmrKcal = bmrKcal;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public void setEstimated(boolean estimated) {
        this.estimated = estimated;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
