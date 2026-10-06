package com.fitsync.analytics.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * A badge the user has earned, with the day it was first earned. Written once and never changed: a new
 * row is always inserted, never merged, so a row stored meanwhile fails on the key instead of being
 * overwritten.
 */
@Entity
@Table(name = "analytics_user_achievements")
@IdClass(AnalyticsUserAchievementKey.class)
public class AnalyticsUserAchievementEntity implements Persistable<AnalyticsUserAchievementKey> {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "earned_on", nullable = false)
    private LocalDate earnedOn;

    @Column(name = "evidence_json", length = 1000)
    private String evidenceJson;

    @Column(name = "created", nullable = false)
    private Instant created;

    @Transient
    private boolean stored;

    @Override
    public AnalyticsUserAchievementKey getId() {
        return new AnalyticsUserAchievementKey(userId, code);
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        this.stored = true;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDate getEarnedOn() {
        return earnedOn;
    }

    public void setEarnedOn(LocalDate earnedOn) {
        this.earnedOn = earnedOn;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }
}
