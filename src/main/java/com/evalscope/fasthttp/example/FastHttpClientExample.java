package com.evalscope.fasthttp.example;

import com.evalscope.fasthttp.batch.BatchExecutor;
import com.evalscope.fasthttp.batch.BatchRequest;
import com.evalscope.fasthttp.batch.BatchResult;
import com.evalscope.fasthttp.client.FastHttpClient;
import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FastHttpClientExample {

    public static void main(String[] args) throws Exception {
        // Example 1: Simple GET request
        simpleGetExample();

        // Example 2: POST request with JSON body
        postWithJsonExample();

        // Example 3: Batch processing of multiple requests
        batchProcessingExample();

        // Example 4: Batch with timeout and cancellation
        batchWithTimeoutExample();
    }

    private static void simpleGetExample() throws Exception {
        System.out.println("=== Simple GET Example ===");

        try (FastHttpClient client = FastHttpClient.builder().build()) {
            Request request = Request.builder()
                    .url("https://httpbin.org/get")
                    .get()
                    .build();

            CompletableFuture<Response> responseFuture = client.execute(request);
            Response response = responseFuture.get(10, TimeUnit.SECONDS);

            System.out.println("Status Code: " + response.code());
            System.out.println("Response Body: " + response.body());
            System.out.println("Elapsed Time: " + response.elapsedTimeMs() + "ms");
        }
    }

    private static void postWithJsonExample() throws Exception {
        System.out.println("\n=== POST with JSON Example ===");

        try (FastHttpClient client = FastHttpClient.builder().build()) {
            Request request = Request.builder()
                    .url("https://httpbin.org/post")
                    .post()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "FastHttpClient/1.0")
                    .body("{\"name\": \"John Doe\", \"email\": \"john@example.com\"}")
                    .build();

            CompletableFuture<Response> responseFuture = client.execute(request);
            Response response = responseFuture.get(10, TimeUnit.SECONDS);

            System.out.println("Status Code: " + response.code());
            System.out.println("Response Body: " + response.body());
            System.out.println("Content Length: " + response.headers().get("Content-Length"));
        }
    }

    private static void batchProcessingExample() throws Exception {
        System.out.println("\n=== Batch Processing Example ===");

        try (BatchExecutor batchExecutor = BatchExecutor.createWithClient()) {
            BatchRequest batchRequest = BatchRequest.builder()
                    .batchId("api-test-batch")
                    .addRequest("get-user", Request.builder()
                            .url("https://httpbin.org/get?id=1")
                            .get()
                            .build())
                    .addRequest("create-user", Request.builder()
                            .url("https://httpbin.org/post")
                            .post()
                            .addHeader("Content-Type", "application/json")
                            .body("{\"userId\": 1, \"name\": \"Test User\"}")
                            .build())
                    .addRequest("update-user", Request.builder()
                            .url("https://httpbin.org/put")
                            .put()
                            .addHeader("Content-Type", "application/json")
                            .body("{\"userId\": 1, \"status\": \"active\"}")
                            .build())
                    .addRequest("delete-user", Request.builder()
                            .url("https://httpbin.org/delete")
                            .delete()
                            .build())
                    .maxConcurrent(2)
                    .build();

            CompletableFuture<BatchResult> resultFuture = batchExecutor.executeAsync(batchRequest);
            BatchResult result = resultFuture.get(30, TimeUnit.SECONDS);

            System.out.println("Batch ID: " + result.batchId());
            System.out.println("Total Requests: " + result.totalRequests());
            System.out.println("Successful: " + result.successfulRequests());
            System.out.println("Failed: " + result.failedRequests());
            System.out.println("Total Time: " + result.totalTimeMs() + "ms");
            System.out.println("Completed: " + result.isCompleted());

            // Print individual results
            for (BatchResult.Result individualResult : result.results()) {
                System.out.println("\nRequest: " + individualResult.requestId());
                System.out.println("  Success: " + individualResult.success());
                System.out.println("  Time: " + individualResult.requestTimeMs() + "ms");

                if (individualResult.success()) {
                    individualResult.response().ifPresent(response -> {
                        System.out.println("  Status Code: " + response.code());
                        if (response.body() != null) {
                            String bodyPreview = response.body().length() > 200
                                ? response.body().substring(0, 200) + "..."
                                : response.body();
                            System.out.println("  Response Preview: " + bodyPreview);
                        }
                    });
                } else {
                    individualResult.error().ifPresent(error -> {
                        System.out.println("  Error: " + error.getMessage());
                    });
                }
            }
        }
    }

    private static void batchWithTimeoutExample() throws Exception {
        System.out.println("\n=== Batch with Timeout Example ===");

        try (BatchExecutor batchExecutor = BatchExecutor.createWithClient()) {
            BatchRequest batchRequest = BatchRequest.builder()
                    .batchId("timeout-test")
                    .addRequest("fast-request", Request.builder()
                            .url("https://httpbin.org/get")
                            .get()
                            .build())
                    .addRequest("slow-request", Request.builder()
                            .url("https://httpbin.org/delay/5")
                            .get()
                            .build(), true) // This is a critical request
                    .maxConcurrent(1) // Execute sequentially
                    .batchTimeout(3000) // Batch timeout: 3 seconds
                    .requestTimeout(2000) // Individual request timeout: 2 seconds
                    .build();

            System.out.println("Starting batch with timeout constraints...");
            long startTime = System.currentTimeMillis();

            CompletableFuture<BatchResult> resultFuture = batchExecutor.executeAsync(batchRequest);
            BatchResult result = resultFuture.get(10, TimeUnit.SECONDS);

            long totalTime = System.currentTimeMillis() - startTime;

            System.out.println("\nBatch completed in: " + totalTime + "ms");
            System.out.println("Batch ID: " + result.batchId());
            System.out.println("Completed: " + result.isCompleted());
            System.out.println("Cancelled: " + result.isCancelled());
            System.out.println("Termination Reason: " + result.terminationReason());
            System.out.println("Successful: " + result.successfulRequests() + "/" + result.totalRequests());

            // Analyze results
            for (BatchResult.Result individualResult : result.results()) {
                System.out.println("\nRequest: " + individualResult.requestId() + " (critical: " + individualResult.isCritical() + ")");
                System.out.println("  Success: " + individualResult.success());
                System.out.println("  Time: " + individualResult.requestTimeMs() + "ms");

                if (!individualResult.success()) {
                    individualResult.error().ifPresent(error -> {
                        System.out.println("  Error: " + error.getClass().getSimpleName() + " - " + error.getMessage());
                    });
                }
            }
        }
    }

    /**
     * Example of creating custom FastHttpClient with specific configuration
     */
    private static void customClientExample() throws Exception {
        System.out.println("\n=== Custom Client Configuration Example ===");

        // Custom EventLoopGroup and Executor
        FastHttpClient client = FastHttpClient.builder()
                .eventLoopGroup(new NioEventLoopGroup(2)) // 2 worker threads
                .executor(Executors.newFixedThreadPool(4)) // 4 executor threads
                .build();

        try {
            Request request = Request.builder()
                    .url("https://httpbin.org/user-agent")
                    .get()
                    .addHeader("User-Agent", "FastHttpClient-Custom/1.0")
                    .build();

            CompletableFuture<Response> responseFuture = client.execute(request);
            Response response = responseFuture.get(10, TimeUnit.SECONDS);

            System.out.println("Status Code: " + response.code());
            System.out.println("Response Body: " + response.body());
        } finally {
            client.close();
        }
    }
}