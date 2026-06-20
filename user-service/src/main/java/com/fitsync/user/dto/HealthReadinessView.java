package com.fitsync.user.dto;

import java.time.Instant;
import java.util.List;

public record HealthReadinessView(
        boolean heartCondition,
        boolean chestPainActivity,
        boolean dizziness,
        boolean boneJointIssue,
        boolean bpOrHeartMeds,
        boolean otherReason,
        List<String> injuryAreas,
        Instant acknowledgedDisclaimerAt) {

    public HealthReadinessView {
        injuryAreas = injuryAreas == null ? List.of() : List.copyOf(injuryAreas);
    }

    @Override
    public List<String> injuryAreas() {
        return List.copyOf(injuryAreas);
    }
}
