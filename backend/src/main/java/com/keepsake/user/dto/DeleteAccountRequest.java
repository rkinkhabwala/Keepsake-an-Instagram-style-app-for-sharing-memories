package com.keepsake.user.dto;

/** Account deletion requires the current password, even with a valid session. */
public record DeleteAccountRequest(String password) {
}
