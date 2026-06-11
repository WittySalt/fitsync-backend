package com.fitsync.user.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.UnitSystem;
import com.fitsync.user.entity.converter.CodeEnumConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_app_preferences")
public class UserAppPreferencesEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = CodeEnumConverters.UnitSystemConverter.class)
    @Column(name = "preferred_units_code", nullable = false, length = 40)
    private UnitSystem preferredUnits;

    @Convert(converter = CodeEnumConverters.LanguagePreferenceConverter.class)
    @Column(name = "language_code", nullable = false, length = 40)
    private LanguagePreference language;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Convert(converter = CodeEnumConverters.PrivacyLevelConverter.class)
    @Column(name = "privacy_level_code", nullable = false, length = 40)
    private PrivacyLevel privacyLevel;

    @Column(name = "data_sharing_consent", nullable = false)
    private boolean dataSharingConsent;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UnitSystem getPreferredUnits() { return preferredUnits; }
    public void setPreferredUnits(UnitSystem preferredUnits) { this.preferredUnits = preferredUnits; }
    public LanguagePreference getLanguage() { return language; }
    public void setLanguage(LanguagePreference language) { this.language = language; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public PrivacyLevel getPrivacyLevel() { return privacyLevel; }
    public void setPrivacyLevel(PrivacyLevel privacyLevel) { this.privacyLevel = privacyLevel; }
    public boolean isDataSharingConsent() { return dataSharingConsent; }
    public void setDataSharingConsent(boolean dataSharingConsent) { this.dataSharingConsent = dataSharingConsent; }
}
