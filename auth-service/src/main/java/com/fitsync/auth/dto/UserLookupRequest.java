package com.fitsync.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The address an admin looks an account up by. */
public record UserLookupRequest(@NotBlank @Size(max = 120) @Email String email) {
}
