package com.smartpay.exception;

import org.springframework.http.HttpStatus;

// One exception for all business rule failures (frozen, insufficient balance, ...).
// It carries the HTTP status that should be returned.
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}