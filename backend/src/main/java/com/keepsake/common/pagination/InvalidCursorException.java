package com.keepsake.common.pagination;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("cursor must be a UUID");
    }
}
