package com.keepsake.auth.service;

public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("Invalid access token");
    }
}
