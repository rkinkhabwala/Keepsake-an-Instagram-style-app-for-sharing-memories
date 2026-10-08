package com.keepsake.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9._]{3,30}$")
        String username,
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
        @NotBlank
        @Size(min = 8, max = 72)
        String password,
        @NotBlank
        @Size(max = 100)
        String displayName
) {
}
