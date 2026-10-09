package com.fitsync.user.service;

import java.util.List;
import java.util.UUID;

import com.fitsync.user.dto.CoachHealthContextView;
import com.fitsync.user.entity.UserAppPreferencesEntity;
import com.fitsync.user.entity.UserHealthReadinessEntity;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserHealthReadinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides what of a user's health answers the coach may see. The gate sits here, in the service that owns the
 * answers, so withdrawing consent stops them from leaving user-service at all rather than relying on each caller
 * to ignore them. Only the injured areas are released even with consent, because they are all the plan draft
 * uses (to exclude exercises).
 */
@Service
public class CoachHealthContextService {

    private final UserAppPreferencesRepository appPreferencesRepository;
    private final UserHealthReadinessRepository healthReadinessRepository;

    public CoachHealthContextService(UserAppPreferencesRepository appPreferencesRepository,
                                     UserHealthReadinessRepository healthReadinessRepository) {
        this.appPreferencesRepository = appPreferencesRepository;
        this.healthReadinessRepository = healthReadinessRepository;
    }

    @Transactional(readOnly = true)
    public CoachHealthContextView forCoach(UUID userId) {
        boolean consent = appPreferencesRepository.findById(userId)
                .map(UserAppPreferencesEntity::isDataSharingConsent)
                .orElse(false);
        if (!consent) {
            return CoachHealthContextView.withoutConsent();
        }
        List<String> injuryAreas = healthReadinessRepository.findById(userId)
                .map(UserHealthReadinessEntity::getInjuryAreas)
                .orElse(List.of());
        return new CoachHealthContextView(true, injuryAreas);
    }
}
