package com.keepsake.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 254)
        String identifier,
        @NotBlank
        @Size(max = 72)
        String password,
        /** The browser's IANA time zone, e.g. Europe/Paris; optional. */
        String timeZone
) {

    public LoginRequest(String identifier, String password) {
        this(identifier, password, null);
    }
}
