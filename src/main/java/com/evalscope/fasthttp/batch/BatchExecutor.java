package com.evalscope.fasthttp.batch;

import com.evalscope.fasthttp.client.FastHttpClient;
import com.evalscope.fasthttp.exception.FastHttpException;
import com.evalscope.fasthttp.http.Request;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.logging.Level;

public class BatchExecutor implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(BatchExecutor.class.getName());

    private final FastHttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BatchExecutor(FastHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient == null");
        this.scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public BatchExecutor(FastHttpClient httpClient, ScheduledExecutorService scheduler) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient == null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler == null");
    }

    public CompletableFuture<BatchResult> executeAsync(BatchRequest batchRequest) {
        if (shutdown.get()) {
            CompletableFuture<BatchResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("BatchExecutor is shutdown"));
            return failed;
        }

        BatchExecutionContext context = new BatchExecutionContext(batchRequest);
        CompletableFuture<BatchResult> resultFuture = new CompletableFuture<>();

        // Schedule batch timeout
        java.util.concurrent.ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            context.cancel("Batch timeout reached: " + batchRequest.batchTimeoutMs() + "ms");
            if (!resultFuture.isDone()) {
                BatchResult result = buildBatchResult(context);
                resultFuture.complete(result);
            }
        }, batchRequest.batchTimeoutMs(), TimeUnit.MILLISECONDS);

        // Execute requests
        executeBatchRequests(batchRequest, context, resultFuture, timeoutFuture);

        return resultFuture;
    }

    private void executeBatchRequests(BatchRequest batchRequest, BatchExecutionContext context,
                                     CompletableFuture<BatchResult> resultFuture,
                                     java.util.concurrent.ScheduledFuture<?> timeoutFuture) {

        int totalRequests = batchRequest.requests().size();
        AtomicInteger completedRequests = new AtomicInteger(0);
        ExecutorService requestExecutor = Executors.newFixedThreadPool(batchRequest.maxConcurrent());

        // Submit all requests
        for (BatchRequest.RequestWrapper wrapper : batchRequest.requests()) {
            if (context.isCancelled()) {
                break;
            }

            requestExecutor.submit(() -> {
                if (context.isCancelled()) {
                    return;
                }

                long requestStartTime = System.currentTimeMillis();

                // Create request with individual timeout
                Request modifiedRequest = wrapper.request().newBuilder()
                        .timeout((int) Math.min(
                            batchRequest.requestTimeoutMs(),
                            batchRequest.batchTimeoutMs() - (System.currentTimeMillis() - context.getStartTime())
                        ))
                        .build();

                try {
                    httpClient.execute(modifiedRequest).whenComplete((response, error) -> {
                        if (context.isCancelled()) {
                            return;
                        }

                        long requestTime = System.currentTimeMillis() - requestStartTime;

                        if (error != null) {
                            BatchResult.Result result = BatchResult.Result.failure(
                                wrapper.requestId(),
                                wrapper.request(),
                                error,
                                requestTime,
                                wrapper.isCritical()
                            );
                            context.addResult(result);

                            // Check if failed request is critical
                            if (wrapper.isCritical()) {
                                context.cancel("Critical request failed: " + wrapper.requestId());
                            }
                        } else {
                            BatchResult.Result result = BatchResult.Result.success(
                                wrapper.requestId(),
                                wrapper.request(),
                                response,
                                requestTime,
                                wrapper.isCritical()
                            );
                            context.addResult(result);
                        }

                        // Check completion
                        int completed = completedRequests.incrementAndGet();
                        if (completed == totalRequests || context.isCancelled() || context.hasCriticalFailures()) {
                            timeoutFuture.cancel(false);
                            requestExecutor.shutdown();
                            BatchResult batchResult = buildBatchResult(context);
                            resultFuture.complete(batchResult);
                        }
                    });
                } catch (Exception e) {
                    long requestTime = System.currentTimeMillis() - requestStartTime;
                    BatchResult.Result result = BatchResult.Result.failure(
                        wrapper.requestId(),
                        wrapper.request(),
                        e,
                        requestTime,
                        wrapper.isCritical()
                    );
                    context.addResult(result);

                    if (wrapper.isCritical()) {
                        context.cancel("Critical request failed: " + wrapper.requestId());
                    }

                    int completed = completedRequests.incrementAndGet();
                    if (completed == totalRequests || context.isCancelled() || context.hasCriticalFailures()) {
                        timeoutFuture.cancel(false);
                        requestExecutor.shutdown();
                        BatchResult batchResult = buildBatchResult(context);
                        resultFuture.complete(batchResult);
                    }
                }
            });
        }
    }

    private BatchResult buildBatchResult(BatchExecutionContext context) {
        BatchResult.Builder builder = BatchResult.builder(context.getBatchId())
                .totalTimeMs(System.currentTimeMillis() - context.getStartTime())
                .completed(!context.isCancelled() && !context.hasCriticalFailures())
                .cancelled(context.isCancelled())
                .terminationReason(context.getCancellationReason());

        // Add all results to the builder
        for (BatchResult.Result result : context.getResults()) {
            builder.addResult(result);
        }

        return builder.build();
    }

    public void cancelBatch(String batchId) {
        // Not implemented in this version - would require tracking active contexts
        logger.log(Level.WARNING, "Batch cancellation requires additional infrastructure");
    }

    @Override
    public void close() throws Exception {
        if (shutdown.compareAndSet(false, true)) {
            try {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class BatchExecutionContext {
        private final String batchId;
        private final long startTime;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<String> cancellationReason = new AtomicReference<>();
        private final List<BatchResult.Result> results = new ArrayList<>();
        private final AtomicBoolean hasCriticalFailures = new AtomicBoolean(false);

        public BatchExecutionContext(BatchRequest batchRequest) {
            this.batchId = batchRequest.batchId();
            this.startTime = System.currentTimeMillis();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel(String reason) {
            if (cancelled.compareAndSet(false, true)) {
                cancellationReason.set(reason);
                logger.log(Level.INFO, "Batch cancelled: " + reason);
            }
        }

        public String getBatchId() {
            return batchId;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getCancellationReason() {
            return cancellationReason.get();
        }

        public void addResult(BatchResult.Result result) {
            synchronized (results) {
                results.add(result);
                if (!result.success() && result.isCritical()) {
                    hasCriticalFailures.set(true);
                }
            }
        }

        public List<BatchResult.Result> getResults() {
            synchronized (results) {
                return new ArrayList<>(results);
            }
        }

        public boolean hasCriticalFailures() {
            return hasCriticalFailures.get();
        }
    }

    public static BatchExecutor createWithClient() {
        return new BatchExecutor(new FastHttpClient.Builder().build());
    }

    public static BatchExecutor createWithClient(FastHttpClient httpClient) {
        return new BatchExecutor(httpClient);
    }
}