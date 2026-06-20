package com.fitsync.user.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertHealthReadinessRequest(
        boolean heartCondition,
        boolean chestPainActivity,
        boolean dizziness,
        boolean boneJointIssue,
        boolean bpOrHeartMeds,
        boolean otherReason,
        @Size(max = 8) List<@Pattern(regexp = "^[A-Z_]{2,40}$") String> injuryAreas,
        @NotNull Instant acknowledgedDisclaimerAt) {

    public UpsertHealthReadinessRequest {
        injuryAreas = injuryAreas == null ? List.of() : List.copyOf(injuryAreas);
    }

    @Override
    public List<String> injuryAreas() {
        return List.copyOf(injuryAreas);
    }
}
