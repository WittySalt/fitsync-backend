package com.fitsync.persistence.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class BaseEffectiveEntity extends BaseAuditableEntity {

    @Column(name = "date_effective_from")
    private LocalDate dateEffectiveFrom;

    @Column(name = "date_effective_to")
    private LocalDate dateEffectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public LocalDate getDateEffectiveFrom() {
        return dateEffectiveFrom;
    }

    public void setDateEffectiveFrom(LocalDate dateEffectiveFrom) {
        this.dateEffectiveFrom = dateEffectiveFrom;
    }

    public LocalDate getDateEffectiveTo() {
        return dateEffectiveTo;
    }

    public void setDateEffectiveTo(LocalDate dateEffectiveTo) {
        this.dateEffectiveTo = dateEffectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
