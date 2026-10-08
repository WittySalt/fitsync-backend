package com.fitsync.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "email": "alice@example.com",
          "displayName": "Alice Tan",
          "password": "ExampleOnlyPassword123!"
        }
        """)
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @Schema(writeOnly = true, example = "ExampleOnlyPassword123!")
        @NotBlank @Size(min = 8, max = 120) String password) {
}
