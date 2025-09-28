package com.evalscope.fasthttp.example;

import com.evalscope.fasthttp.batch.BatchExecutor;
import com.evalscope.fasthttp.batch.BatchRequest;
import com.evalscope.fasthttp.batch.BatchResult;
import com.evalscope.fasthttp.client.PoolableFastHttpClient;
import com.evalscope.fasthttp.http.Request;
import com.evalscope.fasthttp.http.Response;
import com.evalscope.fasthttp.pool.ConnectionPool;
import com.evalscope.fasthttp.pool.ConnectionPoolConfig;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Examples demonstrating FastHttp connection pool configuration and usage
 */
public class ConnectionPoolExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== FastHttp Connection Pool Examples ===\n");

        // Example 1: Default connection pool
        example1DefaultConfig();

        // Example 2: High performance configuration
        example2HighPerformanceConfig();

        // Example 3: Conservative configuration for resource-constrained environments
        example3ConservativeConfig();

        // Example 4: Custom connection pool with queue wait strategy
        example4CustomQueueWaitConfig();

        // Example 5: Connection pool directive reject strategy
        example5DirectRejectStrategy();

        // Example 6: Stress test connection pool limits
        example6StressTest();

        System.out.println("\n✓ All connection pool examples completed!");
    }

    /**
     * Example 1: Default connection pool configuration
     */
    private static void example1DefaultConfig() throws Exception {
        System.out.println("--- Example 1: Default Configuration ---");

        // Create client with default connection pool
        PoolableFastHttpClient client = PoolableFastHttpClient.createDefault();

        try {
            Request request = Request.builder()
                    .url("https://api.ihansen.org/net")
                    .get()
                    .build();

            CompletableFuture<Response> future = client.execute(request);
            Response response = future.get(10, TimeUnit.SECONDS);

            System.out.println("✓ Default pool response: Status " + response.code());
            System.out.println("✓ Pool stats: " + client.getPoolStats());

        } finally {
            client.close();
        }
    }

    /**
     * Example 2: High performance configuration
     */
    private static void example2HighPerformanceConfig() throws Exception {
        System.out.println("\n--- Example 2: High Performance Configuration ---");

        PoolableFastHttpClient client = PoolableFastHttpClient.createHighPerformance();

        try {
            // Create multiple concurrent requests to test high performance pool
            java.util.List<CompletableFuture<Response>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < 10; i++) {
                Request request = Request.builder()
                        .url("https://httpbin.org/get?id=" + i)
                        .get()
                        .addHeader("Request-Id", String.valueOf(i))
                        .build();

                futures.add(client.execute(request));
            }

            // Wait for all requests
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.get(30, TimeUnit.SECONDS);

            int successful = 0;
            for (CompletableFuture<Response> future : futures) {
                try {
                    Response response = future.get();
                    if (response.isSuccessful()) successful++;
                } catch (Exception e) {
                    System.err.println("Request failed: " + e.getMessage());
                }
            }

            System.out.println("✓ High performance pool test: " + successful + "/10 requests successful");
            System.out.println("✓ Pool stats: " + client.getPoolStats());

        } finally {
            client.close();
        }
    }

    /**
     * Example 3: Conservative configuration
     */
    private static void example3ConservativeConfig() throws Exception {
        System.out.println("\n--- Example 3: Conservative Configuration (Resource-Constrained) ---");

        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(20)
                .maxIdleTime(60000) // 60 seconds
                .waitTimeout(10000) // 10 seconds
                .maxConnectionsPerHost(5)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.DIRECT_REJECT)
                .build();

        PoolableFastHttpClient client = PoolableFastHttpClient.builder()
                .connectionPoolConfig(config)
                .build();

        try {
            System.out.println("✓ Conservative pool created");
            System.out.println("✓ Pool config: " + config);
            System.out.println("✓ Pool stats: " + client.getPoolStats());

            // Test a few requests
            for (int i = 0; i < 3; i++) {
                Request request = Request.builder()
                        .url("https://httpbin.org/get?test=" + i)
                        .get()
                        .build();

                CompletableFuture<Response> future = client.execute(request);
                Response response = future.get(10, TimeUnit.SECONDS);
                System.out.println("✓ Request " + i + ": Status " + response.code());
            }

            System.out.println("✓ Final pool stats: " + client.getPoolStats());

        } finally {
            client.close();
        }
    }

    /**
     * Example 4: Queue wait strategy
     */
    private static void example4CustomQueueWaitConfig() throws Exception {
        System.out.println("\n--- Example 4: Queue Wait Strategy ---");

        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(3) // Very low limit to demonstrate queueing
                .waitTimeout(5000) // Wait up to 5 seconds
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT)
                .build();

        PoolableFastHttpClient client = PoolableFastHttpClient.builder()
                .connectionPoolConfig(config)
                .build();

        try {
            System.out.println("✓ Testing queue wait with max 3 connections and waiting strategy");

            // Create more requests than connections (6 requests, 3 connections)
            java.util.List<CompletableFuture<Response>> futures = new java.util.ArrayList<>();

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 6; i++) {
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(100); // Slight delay between requests

                Request request = Request.builder()
                        .url("https://httpbin.org/delay/1?id=" + i) // 1 second delay server response
                        .get()
                        .addHeader("X-Request-Id", String.valueOf(i))
                        .build();

                CompletableFuture<Response> future = client.execute(request);
                futures.add(future);
            }

            // Wait for all requests to complete
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.get(60, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("✓ Queue wait test completed in: " + duration + "ms");
            System.out.println("✓ Pool stats: " + client.getPoolStats());

            int successful = 0;
            for (CompletableFuture<Response> future : futures) {
                try {
                    Response response = future.get();
                    if (response.isSuccessful()) successful++;
                } catch (Exception e) {
                    System.err.println("Request failed after queuing: " + e.getMessage());
                }
            }

            System.out.println("✓ Queue wait result: " + successful + "/6 requests successful");

        } finally {
            client.close();
        }
    }

    /**
     * Example 5: Direct reject strategy
     */
    private static void example5DirectRejectStrategy() throws Exception {
        System.out.println("\n--- Example 5: Direct Reject Strategy ---");

        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(2) // Only 2 connections
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.DIRECT_REJECT)
                .build();

        PoolableFastHttpClient client = PoolableFastHttpClient.builder()
                .connectionPoolConfig(config)
                .build();

        try {
            System.out.println("✓ Testing direct reject with max 2 connections");

            // Attempt to create more connections than allowed
            java.util.List<CompletableFuture<Response>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < 4; i++) {
                Request request = Request.builder()
                        .url("https://httpbin.org/delay/2?id=" + i) // Longer response time
                        .get()
                        .build();

                CompletableFuture<Response> future = client.execute(request);
                futures.add(future);
            }

            int successful = 0;
            int rejected = 0;

            for (CompletableFuture<Response> future : futures) {
                try {
                    Response response = future.get(10, TimeUnit.SECONDS);
                    if (response.isSuccessful()) successful++;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() != null && e.getCause().getMessage().contains("Connection pool is full")) {
                        rejected++;
                        System.out.println("✓ Request rejected due to pool being full: " + e.getCause().getMessage());
                    } else {
                        throw e;
                    }
                }
            }

            System.out.println("✓ Direct reject result: " + successful + " successful, " + rejected + " rejected");
            System.out.println("✓ Pool stats: " + client.getPoolStats());

        } finally {
            client.close();
        }
    }

    /**
     * Example 6: Connection pool stress test
     */
    private static void example6StressTest() throws Exception {
        System.out.println("\n--- Example 6: Connection Pool Stress Test ---");

        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(10)
                .maxIdleTime(5000) // 5 seconds
                .waitTimeout(2000) // 2 seconds
                .maxConnectionsPerHost(3)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.FAIL_FAST)
                .build();

        PoolableFastHttpClient client = PoolableFastHttpClient.builder()
                .connectionPoolConfig(config)
                .build();

        try {
            System.out.println("✓ Initiating stress test with batch processing");

            BatchRequest batchRequest = BatchRequest.builder()
                    .batchId("pool-stress-test")
                    .maxConcurrent(15) // More than pool capacity
                    .requestTimeout(5000)
                    .batchTimeout(15000)
                    .build();

            // Create 25 requests targeting multiple hosts
            for (int i = 0; i < 25; i++) {
                batchRequest.addRequest("req-" + i, Request.builder()
                        .url("https://httpbin.org/get?id=" + i + "\u0026timestamp=" + System.currentTimeMillis())
                        .get()
                        .addHeader("X-Test-Id", "stress-test-" + i)
                        .build());
            }

            try (BatchExecutor batchExecutor = BatchExecutor.createWithClient(client)) {

                CompletableFuture<BatchResult> resultFuture = batchExecutor.executeAsync(batchRequest);
                BatchResult result = resultFuture.get(30, TimeUnit.SECONDS);

                System.out.println("✓ Stress test completed:");
                System.out.println("  Total Requests: " + result.totalRequests());
                System.out.println("  Successful: " + result.successfulRequests());
                System.out.println("  Failed: " + result.failedRequests());
                System.out.println("  Completed: " + result.isCompleted());
                System.out.println("  Cancelled: " + result.isCancelled());
                System.out.println("  Termination reason: " + result.terminationReason());

                // Show connection pool final stats
                System.out.println("✓ Final pool stats: " + client.getPoolStats());

                // Show individual request failures
                for (BatchResult.Result individualResult : result.results()) {
                    if (!individualResult.success()) {
                        individualResult.error().ifPresent(error -> {
                            System.out.println("  ✗ Request " + individualResult.requestId() + " failed: " + error.getMessage());
                        });
                    }
                }
            }

        } finally {
            client.close();
        }
    }

    /**
     * Utility method to check connection pool stats periodically
     */
    private static void monitorPoolStats(PoolableFastHttpClient client, int seconds) throws InterruptedException {
        System.out.println("Monitoring pool stats for " + seconds + " seconds");

        for (int i = 0; i < seconds; i++) {
            System.out.println("Pool stats at second " + (i + 1) + ": " + client.getPoolStats());
            TimeUnit.SECONDS.sleep(1);
        }
    }

    /**
     * Create custom EventLoopGroup and test with connection pool
     */
    private static void advancedConfigurationExample() throws Exception {
        System.out.println("\n--- Advanced Configuration with Custom EventLoopGroup ---");

        // Custom EventLoopGroup for advanced tuning
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(4); // 4 worker threads

        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(50)
                .maxIdleTime(30000)
                .waitTimeout(10000)
                .enableConnectionReuse(true)
                .maxConnectionsPerHost(20)
                .build();

        PoolableFastHttpClient client = PoolableFastHttpClient.builder()
                .connectionPoolConfig(config)
                .build();

        try {
            System.out.println("✓ Testing with custom EventLoopGroup");

            Request request = Request.builder()
                    .url("https://httpbin.org/get")
                    .get()
                    .build();

            CompletableFuture<Response> future = client.execute(request);
            Response response = future.get(10, TimeUnit.SECONDS);

            System.out.println("✓ Advanced config response: Status " + response.code());
            System.out.println("✓ Final pool stats: " + client.getPoolStats());

        } finally {
            client.close();
            eventLoopGroup.shutdownGracefully();
        }
    }

    /**
     * Example showing connection pool monitoring integration
     */
    private static void connectionPoolMonitoringExample() throws Exception {
        System.out.println("\n--- Connection Pool Monitoring Integration ---");

        ConnectionPoolConfig config = ConnectionPoolConfig.builder()
                .maxConnections(15)
                .build();

        PoolableFastHttpClient client = PoolableFastHttpClient.builder()
                .connectionPoolConfig(config)
                .build();

        try {
            System.out.println("✓ Starting connection pool monitoring...");

            // Start monitoring thread
            java.util.concurrent.ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
            monitor.scheduleAtFixedRate(() -> {
                try {
                    ConnectionPool.ConnectionPoolStats stats = client.getPoolStats();
                    System.out.println(
                        String.format("PoolMonitor: Active=%d, Total=%d, Max=%d, Usage=%.1f%%",
                            stats.getActiveConnections(), stats.getTotalConnections(),
                            stats.getMaxConnections(), stats.getUsagePercentage())
                    );
                } catch (Exception e) {
                    System.err.println("PoolMonitor error: " + e.getMessage());
                }
            }, 0, 2, TimeUnit.SECONDS);

            TimeUnit.SECONDS.sleep(5); // Monitor for 5 seconds

            monitor.shutdown();
            monitor.awaitTermination(2, TimeUnit.SECONDS);

            System.out.println("✓ Monitoring completed");
            System.out.println("✓ Final pool stats: " + client.getPoolStats());

        } finally {
            client.close();
        }
    }

    /**
     * Example demonstrating connection pool configuration decision matrix
     */
    private static void configurationDecisionMatrix() throws Exception {
        System.out.println("\n--- Connection Pool Configuration Decision Matrix ---");

        System.out.println("1. Fixed Workload, Predictable Patterns:");
        ConnectionPoolConfig fixedWorkload = ConnectionPoolConfig.builder()
                .maxConnections(50)
                .maxConnectionsPerHost(10)
                .maxIdleTime(30000)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.QUEUE_WAIT)
                .build();
        System.out.println("   " + fixedWorkload);

        System.out.println("\n2. Variable Load, High Permeable Connections:");
        ConnectionPoolConfig variableLoad = ConnectionPoolConfig.builder()
                .maxConnections(200)
                .maxConnectionsPerHost(30)
                .maxIdleTime(15000)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.FAIL_FAST)
                .build();
        System.out.println("   " + variableLoad);

        System.out.println("\n3. Resource-Constrained Environment:");
        ConnectionPoolConfig constrained = ConnectionPoolConfig.builder()
                .maxConnections(30)
                .maxConnectionsPerHost(5)
                .maxIdleTime(60000)
                .overflowStrategy(ConnectionPoolConfig.OverflowStrategy.DIRECT_REJECT)
                .build();
        System.out.println("   " + constrained);
    }

    /**
     * Create different client configurations
     */
    private static void createDifferentConfigurations() {
        System.out.println("\n=== Different FastHttpClient Configuration Examples ===");

        // Default recommended
        PoolableFastHttpClient defaultClient = PoolableFastHttpClient.createDefault();
        System.out.println("Default Client Config: " + defaultClient.getConnectionPoolConfig());

        // High performance
        PoolableFastHttpClient hpClient = PoolableFastHttpClient.createHighPerformance();
        System.out.println("High Perform. Config: " + hpClient.getConnectionPoolConfig());

        // Conservative
        PoolableFastHttpClient conClient = PoolableFastHttpClient.createConservative();
        System.out.println("Conservative Config: " + conClient.getConnectionPoolConfig());

        try {
            defaultClient.close();
            hpClient.close();
            conClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}