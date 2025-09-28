package com.evalscope.fasthttp.batch;

import com.evalscope.fasthttp.client.FastHttpClient;
import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BatchExecutorTest {

    private BatchExecutor batchExecutor;

    @Mock
    private FastHttpClient httpClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        batchExecutor = new BatchExecutor(httpClient);
    }

    @Test
    public void testExecuteAsync_AllRequestsExecuted() throws Exception {
        // Mock HTTP client to return successful responses
        when(httpClient.execute(any(Request.class)))
            .thenReturn(CompletableFuture.completedFuture(Response.success(200, "OK")));

        // Create batch with 5 requests
        BatchRequest batch = BatchRequest.builder()
                .batchId("test-batch-all-requests")
                .maxConcurrent(2)
                .batchTimeout(5000)
                .requestTimeout(3000)
                .build();

        // Add 5 requests
        for (int i = 1; i <= 5; i++) {
            batch.addRequest("req" + i, Request.builder()
                .url("https://example.com/test" + i)
                .get()
                .build());
        }

        // Execute batch
        BatchResult result = batchExecutor.executeAsync(batch)
            .get(10, TimeUnit.SECONDS);

        // Assertions
        assertNotNull(result);
        assertEquals(5, result.results().size(), "All 5 requests should be processed");
  assertEquals(5, result.successfulRequests(), "All 5 requests should be successful");
      assertEquals(0, result.failedRequests(), "No requests should have failed");
        assertTrue(result.isCompleted(), "Batch should be completed");

        System.out.println("SUCCESS: All " + batch.requests().size() + " requests were processed correctly!");
    }
}