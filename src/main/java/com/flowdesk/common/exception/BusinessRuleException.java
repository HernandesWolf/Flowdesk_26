package com.flowdesk.common.exception;

/** A well-formed request that violates a business rule (HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
