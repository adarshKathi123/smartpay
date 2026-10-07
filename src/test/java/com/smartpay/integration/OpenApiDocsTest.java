package com.smartpay.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Starts the real application on a random port and calls it over HTTP.
// Checks that the API documentation is public and lists the main endpoints,
// and that normal endpoints still need a token.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocsTest {

    @Value("${local.server.port}")
    private int port;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void apiDocs_arePublicAndDescribeTheMainEndpoints() throws Exception {
        assertTrue(datasourceUrl.contains("smartpay_test_db"),
                "Refusing to run: the datasource URL is not the test database");

        HttpResponse<String> response = get("/v3/api-docs");

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("/api/auth/login"));
        assertTrue(body.contains("/api/wallet/deposit"));
        assertTrue(body.contains("/api/transfers"));
        assertTrue(body.contains("/api/transactions"));
        assertTrue(body.contains("/api/admin/users/{userId}/freeze"));
        assertTrue(body.contains("bearerAuth"));
        assertTrue(body.contains("Idempotency-Key"));
    }

    @Test
    void swaggerUi_isPublic() throws Exception {
        assertEquals(200, get("/swagger-ui/index.html").statusCode());
    }

    @Test
    void securedEndpoint_withoutToken_returns401() throws Exception {
        assertEquals(401, get("/api/wallet").statusCode());
    }
}