package com.fitsync.user.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.Gender;
import com.fitsync.user.domain.MembershipStatus;
import com.fitsync.user.domain.MembershipTier;
import com.fitsync.user.domain.PrimaryGoal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Column(name = "height_cm", precision = 6, scale = 2)
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 30)
    private ActivityLevel activityLevel;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Column(name = "location_display", length = 120)
    private String locationDisplay;

    @Column(name = "bio", length = 300)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_tier", nullable = false, length = 30)
    private MembershipTier membershipTier = MembershipTier.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 30)
    private MembershipStatus membershipStatus = MembershipStatus.ACTIVE;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_goal", length = 30)
    private PrimaryGoal primaryGoal;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public ActivityLevel getActivityLevel() { return activityLevel; }
    public void setActivityLevel(ActivityLevel activityLevel) { this.activityLevel = activityLevel; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLocationDisplay() { return locationDisplay; }
    public void setLocationDisplay(String locationDisplay) { this.locationDisplay = locationDisplay; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public PrimaryGoal getPrimaryGoal() { return primaryGoal; }
    public void setPrimaryGoal(PrimaryGoal primaryGoal) { this.primaryGoal = primaryGoal; }
    public MembershipTier getMembershipTier() { return membershipTier; }
    public void setMembershipTier(MembershipTier membershipTier) { this.membershipTier = membershipTier; }
    public MembershipStatus getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(MembershipStatus membershipStatus) { this.membershipStatus = membershipStatus; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
}
