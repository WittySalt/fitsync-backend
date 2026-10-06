package com.fitsync.analytics.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A badge the user has earned, with the day it was first earned. Written once and never changed. */
@Entity
@Table(name = "analytics_user_achievements")
@IdClass(AnalyticsUserAchievementKey.class)
public class AnalyticsUserAchievementEntity {

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
