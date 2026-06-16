package com.fitsync.analytics.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_user_daily_body")
@IdClass(AnalyticsUserMetricDateKey.class)
public class AnalyticsUserDailyBodyEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "body_fat_rate", precision = 5, scale = 2)
    private BigDecimal bodyFatRate;

    @Column(name = "waist_cm", precision = 6, scale = 2)
    private BigDecimal waistCm;

    @Column(name = "bmi", precision = 6, scale = 2)
    private BigDecimal bmi;

    @Column(name = "weight_delta_from_start", precision = 6, scale = 2)
    private BigDecimal weightDeltaFromStart;

    @Column(name = "weight_delta_from_previous", precision = 6, scale = 2)
    private BigDecimal weightDeltaFromPrevious;

    @Column(name = "projection_updated_at", nullable = false)
    private Instant projectionUpdatedAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getBodyFatRate() {
        return bodyFatRate;
    }

    public void setBodyFatRate(BigDecimal bodyFatRate) {
        this.bodyFatRate = bodyFatRate;
    }

    public BigDecimal getWaistCm() {
        return waistCm;
    }

    public void setWaistCm(BigDecimal waistCm) {
        this.waistCm = waistCm;
    }

    public BigDecimal getBmi() {
        return bmi;
    }

    public void setBmi(BigDecimal bmi) {
        this.bmi = bmi;
    }

    public BigDecimal getWeightDeltaFromStart() {
        return weightDeltaFromStart;
    }

    public void setWeightDeltaFromStart(BigDecimal weightDeltaFromStart) {
        this.weightDeltaFromStart = weightDeltaFromStart;
    }

    public BigDecimal getWeightDeltaFromPrevious() {
        return weightDeltaFromPrevious;
    }

    public void setWeightDeltaFromPrevious(BigDecimal weightDeltaFromPrevious) {
        this.weightDeltaFromPrevious = weightDeltaFromPrevious;
    }

    public Instant getProjectionUpdatedAt() {
        return projectionUpdatedAt;
    }

    public void setProjectionUpdatedAt(Instant projectionUpdatedAt) {
        this.projectionUpdatedAt = projectionUpdatedAt;
    }
}
