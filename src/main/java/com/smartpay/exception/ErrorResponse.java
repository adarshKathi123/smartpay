package com.smartpay.exception;

import java.time.LocalDateTime;

// The ONE error body used by every error in the API (also for 401 and 403).
// error = short machine-readable code, message = human-readable text.
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {
}