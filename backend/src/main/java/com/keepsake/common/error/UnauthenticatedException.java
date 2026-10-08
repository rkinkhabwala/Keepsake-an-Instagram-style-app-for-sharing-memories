package com.keepsake.common.error;

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Authentication failed");
    }
}
