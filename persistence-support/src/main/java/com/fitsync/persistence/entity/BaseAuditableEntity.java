package com.fitsync.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditableEntity {

    @CreatedDate
    @Column(name = "date_time_created", nullable = false, updatable = false)
    private Instant dateTimeCreated;

    @LastModifiedDate
    @Column(name = "date_time_updated", nullable = false)
    private Instant dateTimeUpdated;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 120)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 120)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public Instant getDateTimeCreated() {
        return dateTimeCreated;
    }

    public Instant getDateTimeUpdated() {
        return dateTimeUpdated;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }
}
