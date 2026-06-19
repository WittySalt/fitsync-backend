package com.fitsync.nutrition.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "water_logs")
public class WaterLogEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "water_id", nullable = false)
    private UUID waterId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_ml", nullable = false)
    private Integer amountMl;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "timezone", length = 80)
    private String timezone;

    public UUID getWaterId() {
        return waterId;
    }

    public void setWaterId(UUID waterId) {
        this.waterId = waterId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Integer getAmountMl() {
        return amountMl;
    }

    public void setAmountMl(Integer amountMl) {
        this.amountMl = amountMl;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Instant loggedAt) {
        this.loggedAt = loggedAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
