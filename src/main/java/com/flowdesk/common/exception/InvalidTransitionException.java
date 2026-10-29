package com.flowdesk.common.exception;

/** The requested workflow transition is not defined from the current state (HTTP 409). */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
