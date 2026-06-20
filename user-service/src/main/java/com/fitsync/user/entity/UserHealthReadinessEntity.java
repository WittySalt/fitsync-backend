package com.fitsync.user.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_health_readiness")
public class UserHealthReadinessEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "heart_condition", nullable = false)
    private boolean heartCondition;

    @Column(name = "chest_pain_activity", nullable = false)
    private boolean chestPainActivity;

    @Column(name = "dizziness", nullable = false)
    private boolean dizziness;

    @Column(name = "bone_joint_issue", nullable = false)
    private boolean boneJointIssue;

    @Column(name = "bp_or_heart_meds", nullable = false)
    private boolean bpOrHeartMeds;

    @Column(name = "other_reason", nullable = false)
    private boolean otherReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_health_readiness_injury_areas", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "injury_area", nullable = false, length = 40)
    private List<String> injuryAreas = new ArrayList<>();

    @Column(name = "acknowledged_disclaimer_at")
    private Instant acknowledgedDisclaimerAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean isHeartCondition() { return heartCondition; }
    public void setHeartCondition(boolean heartCondition) { this.heartCondition = heartCondition; }
    public boolean isChestPainActivity() { return chestPainActivity; }
    public void setChestPainActivity(boolean chestPainActivity) { this.chestPainActivity = chestPainActivity; }
    public boolean isDizziness() { return dizziness; }
    public void setDizziness(boolean dizziness) { this.dizziness = dizziness; }
    public boolean isBoneJointIssue() { return boneJointIssue; }
    public void setBoneJointIssue(boolean boneJointIssue) { this.boneJointIssue = boneJointIssue; }
    public boolean isBpOrHeartMeds() { return bpOrHeartMeds; }
    public void setBpOrHeartMeds(boolean bpOrHeartMeds) { this.bpOrHeartMeds = bpOrHeartMeds; }
    public boolean isOtherReason() { return otherReason; }
    public void setOtherReason(boolean otherReason) { this.otherReason = otherReason; }
    public List<String> getInjuryAreas() { return injuryAreas; }
    public void setInjuryAreas(List<String> injuryAreas) {
        this.injuryAreas = injuryAreas == null ? new ArrayList<>() : new ArrayList<>(injuryAreas);
    }
    public Instant getAcknowledgedDisclaimerAt() { return acknowledgedDisclaimerAt; }
    public void setAcknowledgedDisclaimerAt(Instant acknowledgedDisclaimerAt) {
        this.acknowledgedDisclaimerAt = acknowledgedDisclaimerAt;
    }
}
