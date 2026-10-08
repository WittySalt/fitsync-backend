package com.fitsync.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "email": "alice@example.com",
          "password": "ExampleOnlyPassword123!"
        }
        """)
public record LoginRequest(
        @Email @NotBlank String email,
        @Schema(writeOnly = true, example = "ExampleOnlyPassword123!")
        @NotBlank String password) {
}
