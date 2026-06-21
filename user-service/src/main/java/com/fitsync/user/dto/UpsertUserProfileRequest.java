package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.Gender;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "displayName": "Alice Tan",
          "firstName": "Alice",
          "lastName": "Tan",
          "birthDate": "1995-04-12",
          "gender": "FEMALE",
          "heightCm": 168.5,
          "activityLevel": "MODERATELY_ACTIVE",
          "timezone": "Asia/Singapore",
          "bio": "Training for a stronger 10K.",
          "onboardingCompleted": true
        }
        """)
public record UpsertUserProfileRequest(
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        LocalDate birthDate,
        Gender gender,
        @DecimalMin("80.0") @DecimalMax("300.0")
        BigDecimal heightCm,
        ActivityLevel activityLevel,
        @Size(max = 80) String timezone,
        @Size(max = 120) String locationDisplay,
        @Size(max = 300) String bio,
        boolean onboardingCompleted) {
}
