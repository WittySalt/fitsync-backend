package com.fitsync.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A refresh token as issued (43 base64url characters); anything far longer is not one of ours. */
public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=<redacted>]";
    }
}
