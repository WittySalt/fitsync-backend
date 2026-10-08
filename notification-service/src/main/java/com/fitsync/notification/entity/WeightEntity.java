package com.fitsync.notification.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_weights")
public class WeightEntity {

    @Id
    @Column(name = "metric_id", nullable = false)
    private UUID metricId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    protected WeightEntity() {
    }

    public WeightEntity(UUID metricId, UUID userId, BigDecimal weightKg, Instant measuredAt) {
        this.metricId = metricId;
        this.userId = userId;
        this.weightKg = weightKg;
        this.measuredAt = measuredAt;
    }

    public UUID getMetricId() { return metricId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getWeightKg() { return weightKg; }
    public Instant getMeasuredAt() { return measuredAt; }
}
