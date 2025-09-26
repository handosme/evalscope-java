package com.evalscope.fasthttp.batch;

import com.evalscope.fasthttp.client.FastHttpClient;
import com.evalscope.fasthttp.exception.FastHttpException;
import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BatchExecutorTest {

    private static final Logger logger = Logger.getLogger(BatchExecutorTest.class.getName());
    private FastHttpClient mockClient;
    private BatchExecutor executor;

    @BeforeEach
    public void setUp() {
        mockClient = mock(FastHttpClient.class);
        executor = new BatchExecutor(mockClient);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.close();
        }
    }

    @Test
    @DisplayName("Test successful batch execution")
    public void testSuccessfulBatchExecution() throws Exception {
        // Mock successful responses
        Response mockResponse = Response.builder()
                .request(Request.builder().url("https://example.com").get().build())
                .code(200)
                .message("OK")
                .body("Success")
                .elapsedTimeMs(100)
                .build();

        when(mockClient.execute(any(Request.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BatchRequest batchRequest = BatchRequest.builder()
                .addRequest("request1", Request.builder().url("https://example.com/1").get().build())
                .addRequest("request2", Request.builder().url("https://example.com/2").get().build())
                .maxConcurrent(2)
                .build();

        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);
        BatchResult result = resultFuture.get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(batchRequest.batchId(), result.batchId());
        assertEquals(2, result.totalRequests());
        assertEquals(2, result.successfulRequests());
        assertEquals(0, result.failedRequests());
        assertTrue(result.isCompleted());
        assertFalse(result.isCancelled());
    }

    @Test
    @DisplayName("Test batch with failed requests")
    public void testBatchWithFailedRequests() throws Exception {
        // Mock successful and failed responses
        Response successResponse = Response.builder()
                .request(Request.builder().url("https://example.com").get().build())
                .code(200)
                .message("OK")
                .body("Success")
                .elapsedTimeMs(100)
                .build();

        when(mockClient.execute(any(Request.class)))
                .thenReturn(CompletableFuture.completedFuture(successResponse))
                .thenReturn(CompletableFuture.failedFuture(new FastHttpException("Connection timeout")));

        BatchRequest batchRequest = BatchRequest.builder()
                .addRequest("request1", Request.builder().url("https://example.com/1").get().build())
                .addRequest("request2", Request.builder().url("https://example.com/2").get().build())
                .maxConcurrent(2)
                .build();

        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);
        BatchResult result = resultFuture.get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(2, result.totalRequests());
        assertEquals(1, result.successfulRequests());
        assertEquals(1, result.failedRequests());
        assertTrue(result.isCompleted());
    }

    @Test
    @DisplayName("Test batch timeout functionality")
    public void testBatchTimeout() throws Exception {
        // Create a slow response that will timeout
        CompletableFuture<Response> slowResponse = new CompletableFuture<Response>();

        when(mockClient.execute(any(Request.class)))
                .thenReturn(slowResponse);

        BatchRequest batchRequest = BatchRequest.builder()
                .addRequest("request1", Request.builder().url("https://example.com/1").get().build())
                .addRequest("request2", Request.builder().url("https://example.com/2").get().build())
                .batchTimeOut(2000) // 2 second timeout
                .build();

        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);
        BatchResult result = resultFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(result.isCancelled());
        assertTrue(result.terminationReason().contains("timeout"));
    }

    @Test
    @DisplayName("Test critical request failure handling")
    public void testCriticalRequestFailure() throws Exception {
        // Create responses - first request will fail and it's critical
        when(mockClient.execute(any(Request.class)))
                .thenReturn(CompletableFuture.failedFuture(new FastHttpException("Critical error")))
                .thenReturn(CompletableFuture.completedFuture(
                        Response.builder()
                                .request(Request.builder().url("https://example.com").get().build())
                                .code(200)
                                .message("OK")
                                .elapsedTimeMs(100)
                                .build()
                ));

        BatchRequest batchRequest = BatchRequest.builder()
                .addRequest("critical-request", Request.builder().url("https://example.com/critical").get().build(), true)
                .addRequest("normal-request", Request.builder().url("https://example.com/normal").get().build(), false)
                .maxConcurrent(1)
                .build();

        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);
        BatchResult result = resultFuture.get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertFalse(result.isCompleted());
        assertTrue(result.terminationReason().contains("Critical request failed"));
    }

    @Test
    @DisplayName("Test concurrent execution limits")
    public void testConcurrentExecutionLimits() throws Exception {
        // Mock responses
        Response mockResponse = Response.builder()
                .request(Request.builder().url("https://example.com").get().build())
                .code(200)
                .message("OK")
                .elapsedTimeMs(100)
                .build();

        when(mockClient.execute(any(Request.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // Create batch with 10 requests but limit to 2 concurrent
        BatchRequest.Builder batchBuilder = BatchRequest.builder().maxConcurrent(2);

        for (int i = 0; i < 10; i++) {
            batchBuilder.addRequest("request" + i, Request.builder().url("https://example.com/" + i).get().build());
        }

        BatchRequest batchRequest = batchBuilder.build();

        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);
        BatchResult result = resultFuture.get(30, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(10, result.totalRequests());
        assertEquals(10, result.successfulRequests());
        assertTrue(result.isCompleted());
    }

    @Test
    @DisplayName("Test batch with empty requests throws exception")
    public void testEmptyBatchThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            BatchRequest.builder().build();
        });
    }

    @Test
    @DisplayName("Test executor shutdown")
    public void testExecutorShutdown() throws Exception {
        executor.close();

        BatchRequest batchRequest = BatchRequest.builder()
                .addRequest("request1", Request.builder().url("https://example.com/1").get().build())
                .build();

        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);

        try {
            resultFuture.get(1, TimeUnit.SECONDS);
            fail("Should have thrown exception");
        } catch (Exception e) {
            // Expected - executor is shutdown
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        assertThrows(Exception.class, () -> {
            resultFuture.get(1, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("Test batch request configuration validation")
    public void testBatchRequestConfigurationValidation() {
        // Test invalid concurrent limit
        assertThrows(IllegalArgumentException.class, () -> {
            BatchRequest.builder().maxConcurrent(0);
        });

        // Test invalid timeout
        assertThrows(IllegalArgumentException.class, () -> {
            BatchRequest.builder().batchTimeout(-1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            BatchRequest.builder().requestTimeout(0);
        });
    }

    @ParameterizedTest
    @DisplayName("Test parameter combinations")
    @CsvSource({
            "1, 1000, 500",  // Single request
            "5, 3000, 1000", // Multiple requests
            "10, 5000, 2000" // Large batch
    })
    public void testParameterCombinations(int numRequests, long batchTimeout, long requestTimeout) throws Exception {
        Response mockResponse = Response.builder()
                .request(Request.builder().url("https://example.com").get().build())
                .code(200)
                .message("OK")
                .elapsedTimeMs(50)
                .build();

        when(mockClient.execute(any(Request.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        BatchRequest.Builder batchBuilder = BatchRequest.builder()
                .maxConcurrent(3)
                .batchTimeout(batchTimeout)
                .requestTimeout(requestTimeout);

        for (int i = 0; i < numRequests; i++) {
            batchBuilder.addRequest("request" + i, Request.builder().url("https://example.com/" + i).get().build());
        }

        BatchRequest batchRequest = batchBuilder.build();
        CompletableFuture<BatchResult> resultFuture = executor.executeAsync(batchRequest);
        BatchResult result = resultFuture.get(batchTimeout + 1000, TimeUnit.MILLISECONDS);

        assertNotNull(result);
        assertEquals(numRequests, result.totalRequests());
    }
}