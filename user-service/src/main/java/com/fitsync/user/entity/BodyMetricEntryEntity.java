package com.fitsync.user.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.MetricSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * One body measurement. A deleted entry keeps its row with {@code deletedAt} set, and every query through JPA
 * leaves such rows out ({@link SQLRestriction}).
 */
@Entity
@Table(name = "body_metric_entries")
@SQLRestriction("deleted_at is null")
public class BodyMetricEntryEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "metric_id", nullable = false)
    private UUID metricId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "body_fat_rate", precision = 5, scale = 2)
    private BigDecimal bodyFatRate;

    @Column(name = "waist_cm", precision = 6, scale = 2)
    private BigDecimal waistCm;

    @Column(name = "hip_cm", precision = 6, scale = 2)
    private BigDecimal hipCm;

    @Column(name = "chest_cm", precision = 6, scale = 2)
    private BigDecimal chestCm;

    @Column(name = "arm_cm", precision = 6, scale = 2)
    private BigDecimal armCm;

    @Column(name = "thigh_cm", precision = 6, scale = 2)
    private BigDecimal thighCm;

    @Column(name = "calf_cm", precision = 6, scale = 2)
    private BigDecimal calfCm;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private MetricSource source;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getMetricId() { return metricId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public BigDecimal getBodyFatRate() { return bodyFatRate; }
    public void setBodyFatRate(BigDecimal bodyFatRate) { this.bodyFatRate = bodyFatRate; }
    public BigDecimal getWaistCm() { return waistCm; }
    public void setWaistCm(BigDecimal waistCm) { this.waistCm = waistCm; }
    public BigDecimal getHipCm() { return hipCm; }
    public void setHipCm(BigDecimal hipCm) { this.hipCm = hipCm; }
    public BigDecimal getChestCm() { return chestCm; }
    public void setChestCm(BigDecimal chestCm) { this.chestCm = chestCm; }
    public BigDecimal getArmCm() { return armCm; }
    public void setArmCm(BigDecimal armCm) { this.armCm = armCm; }
    public BigDecimal getThighCm() { return thighCm; }
    public void setThighCm(BigDecimal thighCm) { this.thighCm = thighCm; }
    public BigDecimal getCalfCm() { return calfCm; }
    public void setCalfCm(BigDecimal calfCm) { this.calfCm = calfCm; }
    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant measuredAt) { this.measuredAt = measuredAt; }
    public MetricSource getSource() { return source; }
    public void setSource(MetricSource source) { this.source = source; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
