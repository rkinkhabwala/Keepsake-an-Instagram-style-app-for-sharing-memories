package com.keepsake.common.error;

/** The caller is signed in but may not do this; unlike 401, it does not mean the session has expired. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
