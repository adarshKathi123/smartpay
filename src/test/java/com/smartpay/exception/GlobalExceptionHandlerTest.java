package com.smartpay.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }

    @Test
    void businessRule_usesStatusNameAsErrorCode() {
        BusinessRuleException ex = new BusinessRuleException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessRule(ex, request("/api/transfers"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(422, body.status());
        assertEquals("UNPROCESSABLE_ENTITY", body.error());
        assertEquals("Insufficient balance", body.message());
        assertEquals("/api/transfers", body.path());
        assertNotNull(body.timestamp());
    }

    @Test
    void emailAlreadyExists_keepsItsErrorCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleEmailAlreadyExists(new EmailAlreadyExistsException(), request("/api/auth/register"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("EMAIL_ALREADY_EXISTS", response.getBody().error());
        assertEquals("/api/auth/register", response.getBody().path());
    }

    @Test
    void accessDenied_returns403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("no"), request("/api/admin/users/1/freeze"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("FORBIDDEN", response.getBody().error());
    }

    @Test
    void unexpectedException_returns500WithoutLeakingDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("secret internal detail"), request("/api/wallet"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().error());
        assertFalse(response.getBody().message().contains("secret"));
    }
}