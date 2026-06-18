package com.fitsync.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRecoveryEmailRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 120) String currentPassword) {
}
