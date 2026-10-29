package com.flowdesk.common.exception;

/** Also used when a resource exists but belongs to another tenant, so existence is never leaked. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
