package com.smartpay.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Starts the real application and checks that EVERY kind of error has the same JSON shape:
// timestamp, status, error, message, path. Runs against smartpay_test_db.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorFormatIntegrationTest {

    private static final String PASSWORD = "Test@12345";

    @Value("${local.server.port}")
    private int port;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void onlyRunOnTestDatabase() {
        assertTrue(datasourceUrl.contains("smartpay_test_db"),
                "Refusing to run: the datasource URL is not the test database");
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertErrorBody(HttpResponse<String> response, int status, String error, String path) {
        String body = response.body();
        assertEquals(status, response.statusCode(), body);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"), body);
        assertTrue(body.contains("\"timestamp\""), body);
        assertTrue(body.contains("\"status\":" + status), body);
        assertTrue(body.contains("\"error\":\"" + error + "\""), body);
        assertTrue(body.contains("\"message\""), body);
        assertTrue(body.contains("\"path\":\"" + path + "\""), body);
    }

    private String registerUser() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String json = "{\"name\":\"Error Tester\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        assertEquals(201, send("POST", "/api/auth/register", null, json).statusCode());
        return email;
    }

    private String loginAndGetToken(String email) throws Exception {
        String json = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        HttpResponse<String> response = send("POST", "/api/auth/login", null, json);
        assertEquals(200, response.statusCode(), response.body());
        Matcher matcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
        assertTrue(matcher.find(), response.body());
        return matcher.group(1);
    }

    @Test
    void missingToken_returnsJson401() throws Exception {
        assertErrorBody(send("GET", "/api/wallet", null, null), 401, "UNAUTHORIZED", "/api/wallet");
    }

    @Test
    void invalidToken_returnsJson401() throws Exception {
        assertErrorBody(send("GET", "/api/wallet", "not-a-real-token", null), 401, "UNAUTHORIZED", "/api/wallet");
    }

    @Test
    void malformedJson_returnsJson400() throws Exception {
        assertErrorBody(send("POST", "/api/auth/register", null, "{bad"),
                400, "MALFORMED_REQUEST", "/api/auth/register");
    }

    @Test
    void invalidBody_returnsJson400() throws Exception {
        assertErrorBody(send("POST", "/api/auth/register", null, "{\"name\":\"\",\"email\":\"x\",\"password\":\"1\"}"),
                400, "VALIDATION_FAILED", "/api/auth/register");
    }

    @Test
    void wrongPassword_returnsJson401() throws Exception {
        String email = registerUser();
        String json = "{\"email\":\"" + email + "\",\"password\":\"Wrong@12345\"}";
        assertErrorBody(send("POST", "/api/auth/login", null, json),
                401, "INVALID_CREDENTIALS", "/api/auth/login");
    }

    @Test
    void unknownPath_returnsJson404() throws Exception {
        String token = loginAndGetToken(registerUser());
        assertErrorBody(send("GET", "/api/does-not-exist", token, null),
                404, "NOT_FOUND", "/api/does-not-exist");
    }

    @Test
    void wrongHttpMethod_returnsJson405() throws Exception {
        String token = loginAndGetToken(registerUser());
        assertErrorBody(send("POST", "/api/wallet", token, "{}"),
                405, "METHOD_NOT_ALLOWED", "/api/wallet");
    }

    @Test
    void normalUserCallingAdminEndpoint_returnsJson403() throws Exception {
        String token = loginAndGetToken(registerUser());
        assertErrorBody(send("POST", "/api/admin/users/1/freeze", token, null),
                403, "FORBIDDEN", "/api/admin/users/1/freeze");
    }
}