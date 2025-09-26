package com.evalscope.fasthttp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

public class RequestTest {

    @Test
    @DisplayName("Test basic GET request building")
    public void testBasicGetRequest() {
        Request request = new Request.Builder()
                .url("https://example.com/api")
                .get()
                .build();

        assertEquals("https://example.com/api", request.url());
        assertEquals("GET", request.method());
        assertNotNull(request.headers());
        assertNull(request.body());
        assertEquals(30000, request.timeoutMillis());
    }

    @Test
    @DisplayName("Test POST request with body and headers")
    public void testPostRequestWithBodyAndHeaders() {
        Request request = new Request.Builder()
                .url("https://api.example.com/users")
                .post()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer token123")
                .body('{\"name\": \"John Doe\", \"email\": \"john@example.com\"}')
                .build();

        assertEquals("https://api.example.com/users", request.url());
        assertEquals("POST", request.method());
        assertEquals('{\"name\": \"John Doe\", \"email\": \"john@example.com\"}', request.body());

        Headers headers = request.headers();
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("Test custom timeout")
    public void testCustomTimeout() {
        Request request = new Request.Builder()
                .url("https://example.com")
                .timeout(60000) // 60 seconds
                .build();

        assertEquals(60000, request.timeoutMillis());
    }

    @Test
    @DisplayName("Test multiple headers with same name")
    public void testMultipleHeadersWithSameName() {
        Request request = new Request.Builder()
                .url("https://example.com")
                .addHeader("Accept", "application/json")
                .addHeader("Accept", "text/html")
                .build();

        Headers headers = request.headers();
        List<String> acceptValues = headers.values("Accept");
        assertEquals(2, acceptValues.size());
        assertTrue(acceptValues.contains("application/json"));
        assertTrue(acceptValues.contains("text/html"));
    }

    @ParameterizedTest
    @DisplayName("Test different HTTP methods")
    @CsvSource({"GET,GET", "POST,POST", "PUT,PUT", "DELETE,DELETE"})
    public void testDifferentHttpMethods(String methodName, String expected) {
        Request request = new Request.Builder()
                .url("https://example.com")
                .method(methodName)
                .build();

        assertEquals(expected, request.method());
    }

    @Test
    @DisplayName("Test request builder validation")
    public void testRequestBuilderValidation() {
        // Test null URL
        assertThrows(NullPointerException.class, () -> {
            new Request.Builder().url(null).build();
        });

        // Test null method
        assertThrows(NullPointerException.class, () -> {
            new Request.Builder().url("https://example.com").method(null).build();
        });

        // Test negative timeout
        assertThrows(IllegalArgumentException.class, () -> {
            new Request.Builder().url("https://example.com").timeout(-1).build();
        });

        // Test missing URL
        assertThrows(IllegalStateException.class, () -> {
            new Request.Builder().build();
        });
    }

    @Test
    @DisplayName("Test request with header line parsing")
    public void testHeaderLineParsing() {
        Request request = new Request.Builder()
                .url("https://example.com")
                .addHeaderLine("Accept: application/json")
                .addHeaderLine("Content-Type: application/json")
                .addHeaderLine("Authorization: Bearer token123")
                .build();

        Headers headers = request.headers();
        assertEquals("application/json", headers.get("Accept"));
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("Test request builder reuse")
    public void testRequestBuilderReuse() {
        Request.Builder builder = new Request.Builder()
                .url("https://example.com")
                .addHeader("Authorization", "Bearer token123");

        Request request1 = builder.build();
        Request request2 = builder.build();

        assertEquals(request1.url(), request2.url());
        assertEquals(request1.headers().get("Authorization"), request2.headers().get("Authorization"));
    }

    @Test
    @DisplayName("Test request newBuilder")
    public void testRequestNewBuilder() {
        Request original = new Request.Builder()
                .url("https://example.com")
                .post()
                .addHeader("Authorization", "Bearer original")
                .body('{"test": true}')
                .timeout(45000)
                .build();

        Request modified = original.newBuilder()
                .addHeader("Authorization", "Bearer modified")
                .build();

        assertEquals(original.url(), modified.url());
        assertEquals(original.method(), modified.method());
        assertEquals(original.body(), modified.body());
        assertEquals(original.timeoutMillis(), modified.timeoutMillis());

        // Should have both headers
        assertEquals("Bearer original", modified.headers().values("Authorization").get(0));
        assertEquals("Bearer modified", modified.headers().values("Authorization").get(1));
    }
}