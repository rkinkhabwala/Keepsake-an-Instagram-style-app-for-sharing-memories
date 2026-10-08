package com.keepsake.user.dto;

import jakarta.validation.constraints.Size;

/** PATCH body: a null field leaves the stored value unchanged; an empty bio clears it. */
public record UpdateProfileRequest(
        @Size(max = 100)
        String displayName,
        @Size(max = 150)
        String bio,
        Boolean isPrivate
) {
}
