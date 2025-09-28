package com.evalscope.fasthttp.batch;

import com.evalscope.fasthttp.client.FastHttpClient;
import com.evalscope.fasthttp.client.PoolableFastHttpClient;
import com.evalscope.fasthttp.exception.FastHttpException;
import com.evalscope.fasthttp.http.Request;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 批量HTTP请求执行器
 *
 * 该类负责同时执行多个HTTP请求，提供以下主要功能：
 * - 并发控制：限制同时运行的请求数，防止对目标服务器造成过大压力
 * - 超时处理：支持单个请求超时和整个批次的超时
 * - 重要请求标记：某些请求失败时可以自动取消整个批次
 * - 结果统计：提供详细的执行统计信息
 * - 连接池支持：与PoolableFastHttpClient完美集成，提高性能
 *
 * 使用示例：
 * <pre>
 * // 创建客户端
 * PoolableFastHttpClient client = PoolableFastHttpClient.createDefault();
 *
 * // 创建批量执行器
 * try (BatchExecutor executor = BatchExecutor.createWithClient(client)) {
 *     // 创建批量请求
 *     BatchRequest batch = BatchRequest.builder()
 *         .batchId("api-test")
 *         .maxConcurrent(3)           // 最多3个请求同时执行
 *         .batchTimeout(30000)        // 整个批次30秒超时
 *         .requestTimeout(5000)       // 单个请求5秒超时
 *         .build();
 *
 *     // 添加请求
 *     batch.addRequest("req1", Request.builder().url("https://api.com/users/1").get().build());
 *     batch.addRequest("req2", Request.builder().url("https://api.com/users/2").get().build());
 *
 *     // 执行批量请求
 *     BatchResult result = executor.executeAsync(batch).get();
 *
 *     System.out.println("成功请求数：" + result.successfulRequests());
 *     System.out.println("失败请求数：" + result.failedRequests());
 *
 *     // 获取某个请求的结果
 *     Optional<BatchResult.Result> requestResult = result.result("req1");
 *     requestResult.ifPresent(r -> {
 *         if (r.success()) {
 *             System.out.println("请求成功，响应码：" + r.response().get().code());
 *         } else {
 *             System.err.println("请求失败：" + r.error().get().getMessage());
 *         }
 *     });
 * }
 * </pre>
 */
public class BatchExecutor implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(BatchExecutor.class.getName());

    /**
     * HTTP客户端，负责具体的请求发送
     * 可以是FastHttpClient或PoolableFastHttpClient（带连接池）
     */
    private final FastHttpClient httpClient;

    /**
     * 调度执行器，用于处理超时和并发控制
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 关闭状态标识，确保只关闭一次
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 创建批量执行器实例，指定HTTP客户端
     *
     * @param httpClient HTTP客户端，必须使用支持并发访问的实现
     */
    public BatchExecutor(FastHttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient == null");
        this.scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * 创建批量执行器实例，指定HTTP客户端和调度服务
     *
     * @param httpClient HTTP客户端
     * @param scheduler 自定义的调度执行器服务
     */
    public BatchExecutor(FastHttpClient httpClient, ScheduledExecutorService scheduler) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient == null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler == null");
    }

    /**
     * 异步执行批量HTTP请求
     *
     * @param batchRequest 批量请求对象，包含需要执行的请求列表和各项配置
     * @return CompletableFuture响应，完成后返回BatchResult对象，包含所有请求的执行结果
     * @throws IllegalStateException 如果执行器已关闭
     * @throws NullPointerException 如果batchRequest为空
     *
     * 该方法的主要工作流程：
     * 1. 创建批处理上下文，用于跟踪执行状态
     * 2. 调度批次超时任务，防止无限期等待
     * 3. 创建固定的线程池，控制并发请求数量
     * 4. 为每个请求创建单独的future，监控执行状态
     * 5. 所有请求完成后，汇总结果，构建BatchResult对象
     */
    public CompletableFuture<BatchResult> executeAsync(BatchRequest batchRequest) {
        if (shutdown.get()) {
            CompletableFuture<BatchResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("BatchExecutor is shutdown"));
            return failed;
        }

        BatchExecutionContext context = new BatchExecutionContext(batchRequest);
        CompletableFuture<BatchResult> resultFuture = new CompletableFuture<>();

        // 调度批次超时任务 - 当批次执行时间超过设定的最大时间时，自动取消剩余请求
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            context.cancel("批次执行超时，最大允许时间：" + batchRequest.batchTimeoutMs() + "毫秒");
            if (!resultFuture.isDone()) {
                BatchResult result = buildBatchResult(context);
                resultFuture.complete(result);
            }
        }, batchRequest.batchTimeoutMs(), TimeUnit.MILLISECONDS);

        // 执行批量请求 - 主要的工作流程如下：
        // 1. 创建指定数量的工作线程（最多maxConcurrent个）
        // 2. 从队列中获取请求，发送给HTTP客户端
        // 3. 等待所有请求完成或被取消
        // 4. 收集结果，构建最终结果对象
        executeBatchRequests(batchRequest, context, resultFuture, timeoutFuture);

        return resultFuture;
    }

    /**
     * 执行批量请求的核心方法
     *
     * 该方法负责实际执行所有的HTTP请求，主要处理逻辑包括：
     * - 控制并发数量：根据maxConcurrent参数限制同时执行的请求数
     * - 请求超时处理：每个请求使用独立的超时设置（不超过批次剩余时间）
     * - 重要请求失败处理：当关键请求失败时，自动取消整个批次
     * - 异步结果收集：使用CompletableFuture监控每个请求的完成情况
     * - 生命周期管理：妥善处理线程池和资源释放
     *
     * @param batchRequest 批量请求配置对象
     * @param context 批处理执行上下文，用于跟踪执行状态
     * @param resultFuture 整个批次的结果Future
     * @param timeoutFuture 批次超时任务的Future引用（用于在批次完成时取消定时器）
     */
    private void executeBatchRequests(BatchRequest batchRequest, BatchExecutionContext context,
                                     CompletableFuture<BatchResult> resultFuture,
                                     java.util.concurrent.ScheduledFuture<?> timeoutFuture) {
        int totalRequests = batchRequest.requests().size();
        AtomicInteger completedRequests = new AtomicInteger(0);
        AtomicInteger submittedRequests = new AtomicInteger(0);
        ExecutorService requestExecutor = Executors.newFixedThreadPool(batchRequest.maxConcurrent());

        // 提交所有请求 - 注意：这里使用maxConcurrent控制并发数量，
        // 实际并发数不会超过批次的maxConcurrent设置
        for (BatchRequest.RequestWrapper wrapper : batchRequest.requests()) {
            if (context.isCancelled()) {
                break;
            }

            submittedRequests.incrementAndGet();
            requestExecutor.submit(() -> {
                // 如果批次已经取消，则跳过该请求
                if (context.isCancelled()) {
                    return;
                }

                long requestStartTime = System.currentTimeMillis();

                // 创建请求，计算最佳的超时时间 - 确保不超过批次剩余时间
                Request modifiedRequest = wrapper.request().newBuilder()
                        .timeout((int) Math.min(
                            batchRequest.requestTimeoutMs(),
                            batchRequest.batchTimeoutMs() - (System.currentTimeMillis() - context.getStartTime())
                        ))
                        .build();

                try {
                    // 发送HTTP请求到目标服务器 -- 使用 HTTP 客户端异步执行
                    httpClient.execute(modifiedRequest).whenComplete((response, error) -> {
                        // 如果批次已被取消，则忽略当前请求的响应
                        if (context.isCancelled()) {
                            return;
                        }

                        long requestTime = System.currentTimeMillis() - requestStartTime;

                        // 处理请求错误结果
                        if (error != null) {
                            // 请求发送失败 -- 构建失败结果并记录
                            BatchResult.Result result = BatchResult.Result.failure(
                                wrapper.requestId(),
                                wrapper.request(),
                                error,
                                requestTime,
                                wrapper.isCritical()
                            );
                            context.addResult(result);

                            // 如果请求是关键请求，则取消整个批次
                            // 这样可以快速停止不相关的请求，节省资源
                            if (wrapper.isCritical()) {
                                context.cancel("Critical request failed: " + wrapper.requestId());
                            }
                        // 处理请求成功结果
                        } else {
                            // 请求成功 -- 构建成功结果
                            BatchResult.Result result = BatchResult.Result.success(
                                wrapper.requestId(),
                                wrapper.request(),
                                response,
                                requestTime,
                                wrapper.isCritical()
                            );
                            context.addResult(result);
                        }

                        // 记录完成的请求并检查是否所有请求都已完成
                        int completed = completedRequests.incrementAndGet();
                        logger.log(Level.FINE, String.format("请求完成: %s (已完成 %d/%d)",
                            wrapper.requestId(), completed, totalRequests));

                        // 使用安全的方法检查批次完成状态
                        completeBatchSafely(requestExecutor, context, resultFuture, timeoutFuture,
                                          completedRequests, submittedRequests, totalRequests);
                    });
                } catch (Exception e) {
                    // 请求发送异常处理 - 通常发生在请求构建或发送阶段，需要在这里立即处理结果
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
                    if (completed == submittedRequests.get() || context.isCancelled() || context.hasCriticalFailures()) {
                        timeoutFuture.cancel(false);
                        requestExecutor.shutdown();
                        BatchResult batchResult = buildBatchResult(context);
                        resultFuture.complete(batchResult);
                    }
                }
            });
        }
    }

    /**
     * 构建批量执行结果对象
     *
     * 该方法汇总所有请求的执行结果，包括成功、失败、取消状态，
     * 以及总耗时、执行状态等关键指标。所有的执行统计信息都会被收集和整理。
     *
     * @param context 执行上下文，包含所有请求的执行状态和统计信息
     * @return 批量执行结果，包含完整的执行统计和每个请求的详细结果
     */
    private BatchResult buildBatchResult(BatchExecutionContext context) {
        BatchResult.Builder builder = BatchResult.builder(context.getBatchId())
                .totalTimeMs(System.currentTimeMillis() - context.getStartTime())
                .completed(!context.isCancelled() && !context.hasCriticalFailures())
                .cancelled(context.isCancelled())
                .terminationReason(context.getCancellationReason());

        // 添加所有结果到builder
        for (BatchResult.Result result : context.getResults()) {
            builder.addResult(result);
        }

        return builder.build();
    }

    /**
     * 安全地完成批次执行，确保所有请求都已处理或确认不会执行
     *
     * @param requestExecutor 请求执行器
     * @param context 执行上下文
     * @param resultFuture 结果Future
     * @param timeoutFuture 超时定时器Future
     * @param completedRequests 已完成请求数
     * @param submittedRequests 已提交请求数
     * @param totalRequests 总请求数
     */
    private void completeBatchSafely(ExecutorService requestExecutor,
                                     BatchExecutionContext context,
                                     CompletableFuture<BatchResult> resultFuture,
                                     java.util.concurrent.ScheduledFuture<?> timeoutFuture,
                                     AtomicInteger completedRequests,
                                     AtomicInteger submittedRequests,
                                     int totalRequests) {
        logger.log(Level.INFO, String.format("批次 %s 完成检测: 提交 %d/%d, 完成 %d/%d, 取消 %s, 关键失败 %s",
            context.getBatchId(),
            submittedRequests.get(), totalRequests,
            completedRequests.get(), totalRequests,
            context.isCancelled(),
            context.hasCriticalFailures()));

        // 只有在真正完成所有工作后才执行清理
        if (completedRequests.get() == submittedRequests.get() ||
            context.isCancelled() ||
            context.hasCriticalFailures()) {

            // 取消超时定时器
            timeoutFuture.cancel(false);

            // 先关闭执行器，不再接受新任务
            requestExecutor.shutdown();

            try {
                // 给正在执行的任务一点时间完成（最多等待2秒）
                if (!requestExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "批次 " + context.getBatchId() + " 有任务未能在2秒内完成，强制关闭");
                    requestExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "批次 " + context.getBatchId() + " 执行器关闭被中断，强制关闭");
                requestExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 构建最终结果
            BatchResult batchResult = buildBatchResult(context);

            logger.log(Level.INFO, String.format("批次 %s 结果: 总请求 %d, 成功 %d, 失败 %d, 状态: %s - %s",
                context.getBatchId(),
                totalRequests,
                batchResult.successfulRequests(),
                batchResult.failedRequests(),
                batchResult.isCompleted() ? "完成" : "未完成",
                batchResult.terminationReason() != null ? batchResult.terminationReason() : "正常"));

            // 只完成一次 - 防止重复完成
            resultFuture.complete(batchResult);
        }
    }

    /**
     * 取消指定批次执行（预留功能扩展）
     *
     * 当前版本未实现此功能，需要在执行过程中维护活跃的执行上下文映射。
     * 实现思路：可以维护一个 batchId -> ActiveContext 的 Map，按照需要随时取消。
     *
     * @param batchId 要取消的批次ID
     * @apiNote 此方法需要在后续版本中添加实时上下文跟踪功能后实现
     */
    public void cancelBatch(String batchId) {
        // 未实现 - 需要维护活跃执行上下文映射
        logger.log(Level.WARNING, "批量取消操作需要在后续版本中实现活跃上下文跟踪功能后支持");
    }

    /**
     * 关闭批量执行器并清理资源
     *
     * 该方法会：
     * - 关闭运行中的HTTP客户端
     * - 优雅关闭调度器服务（等待30秒）
     * - 中断并强制关闭超时的工作线程
     * - 设置关闭状态标识，防止重复关闭
     *
     * @throws Exception 如果在关闭过程中发生异常
     * @apiNote 建议使用try-with-resources自动管理资源
     */
    @Override
    public void close() throws Exception {
        if (shutdown.compareAndSet(false, true)) {
            try {
                // 关闭HTTP客户端
                httpClient.close();

                // 优雅关闭调度器服务
                scheduler.shutdown();
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                // 如果关闭被中断，强制关闭调度器
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class BatchExecutionContext {
        /** 批次ID，用于标识这个批次的唯一名称 */
        private final String batchId;

        /** 批次开始执行的时间戳，用于计算执行耗时 */
        private final long startTime;

        /** 批次取消状态标记 */
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        /** 批次取消的具体原因，便于问题分析 */
        private final AtomicReference<String> cancellationReason = new AtomicReference<>();

        /** 所有请求的详细执行结果列表 */
        private final List<BatchResult.Result> results = new ArrayList<>();

        /** 标记是否存在关键请求失败的情况 */
        private final AtomicBoolean hasCriticalFailures = new AtomicBoolean(false);

        /**
         * 创建执行上下文对象
         *
         * @param batchRequest 批次请求实例，用于初始化批次ID
         */
        public BatchExecutionContext(BatchRequest batchRequest) {
            this.batchId = batchRequest.batchId();
            this.startTime = System.currentTimeMillis();
        }

        /**
         * 检查批次是否已被取消
         *
         * @return true 如果批次已被取消，false 否则
         * @apiNote 取消状态一旦设置为true，不会再改变
         */
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

    /**
     * 使用指定的 FastHttpClient 实例创建批量执行器
     *
     * 适用于自定义客户端的高级场景，也支持传递 PoolableFastHttpClient。
     * 建议使用带连接池的客户端以获得最佳性能。
     *
     * @param httpClient HTTP客户端实例（建议使用连接池实现）
     * @return 配置的批量执行器
     * @throws NullPointerException 如果客户端为空
     */
    public static BatchExecutor createWithClient(FastHttpClient httpClient) {
        return new BatchExecutor(httpClient);
    }
}