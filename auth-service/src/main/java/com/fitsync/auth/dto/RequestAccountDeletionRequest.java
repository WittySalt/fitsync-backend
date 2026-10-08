package com.fitsync.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestAccountDeletionRequest(
        @NotBlank @Size(min = 6, max = 120) String confirmationText,
        @Size(max = 120) String currentPassword) {
}
