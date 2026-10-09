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
        String displayName,
        /** The browser's IANA time zone, e.g. Europe/Paris; optional. */
        String timeZone
) {

    public SignupRequest(String username, String email, String password, String displayName) {
        this(username, email, password, displayName, null);
    }
}
