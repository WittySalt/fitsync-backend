package com.fitsync.user.dto;

import java.util.List;

/**
 * The part of the readiness questionnaire the coach may use when it builds a plan: only the injured areas, and
 * only when the user has consented to their health answers being processed. Without consent the list is empty
 * and {@code healthDataConsent} is false, so the answers never leave user-service.
 */
public record CoachHealthContextView(boolean healthDataConsent, List<String> injuryAreas) {

    public CoachHealthContextView {
        injuryAreas = injuryAreas == null ? List.of() : List.copyOf(injuryAreas);
    }

    @Override
    public List<String> injuryAreas() {
        return List.copyOf(injuryAreas);
    }

    public static CoachHealthContextView withoutConsent() {
        return new CoachHealthContextView(false, List.of());
    }
}
