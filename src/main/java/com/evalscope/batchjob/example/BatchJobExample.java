package com.evalscope.batchjob.example;

import com.evalscope.batchjob.BatchJob;
import com.evalscope.batchjob.BatchJobConfig;
import com.evalscope.batchjob.BatchJobResult;
import com.evalscope.batchjob.model.BatchRequest;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 批处理模块使用示例
 * 展示如何使用BatchJob执行大批量的大模型API调用
 */
public class BatchJobExample {

    public static void main(String[] args) {
        try {
            runExample();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("批处理执行出错: " + e.getMessage());
            e.printStackTrace();
            System.err.println("这可能是因为网络问题或API配置错误");
        } catch (TimeoutException e) {
            System.err.println("批处理超时: " + e.getMessage());
            e.printStackTrace();
            System.err.println("请考虑增加请求超时时间或减少请求数量");
        }
    }

    private static void runExample() throws InterruptedException, ExecutionException, TimeoutException {
        // 创建配置
        BatchJobConfig config = BatchJobConfig.builder()
                .apiEndpoint("https://photo.ihansen.org/")
                .apiKey("your-api-key-here")
                .maxConcurrentRequests(1)
                .connectionTimeout(5000)
                .requestTimeout(30000)
                .threadPoolSize(8)
                .maxBatchSize(20)
                .batchExecutionTimeout(600_000)
                .build();

        // 创建批处理器
        BatchJob batchJob = new BatchJob(config);

        try {
            // 初始化批处理器
            batchJob.initialize();

            // 创建批量请求
            List<BatchRequest> requests = createBatchRequests(3);

            System.out.println("开始处理批量请求...");
            long startTime = System.currentTimeMillis();

            // 方法1：使用CompletableFuture
            CompletableFuture<BatchJobResult> future = batchJob.processBatch(requests);

            // 等待结果完成（带超时时间，防止无限等待）
            BatchJobResult result = future.get(3 * config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            long endTime = System.currentTimeMillis();
            System.out.println("批处理完成，耗时: " + (endTime - startTime) + "ms");
            System.out.println(result.getSummary());

            // 方法2：使用回调 - 使用超时机制防止无限等待
            System.out.println("\n使用回调方式处理批量请求...");

            final Duration callbackTimeout = Duration.ofMillis(Math.max(config.getBatchExecutionTimeout(),
                    (long)(config.getRequestTimeout() * 1.2))); // 使用与之前类似的计算
            final long callbackStartTime = System.currentTimeMillis();

            CompletableFuture<Void> resultFuture = new CompletableFuture<>();
            batchJob.processBatchWithCallback(requests, callbackResult -> {
                long callbackEndTime = System.currentTimeMillis();
                System.out.println("回调方式批处理完成，耗时: " + (callbackEndTime - callbackStartTime) + "ms");
                System.out.println(callbackResult.getSummary());
                resultFuture.complete(null);
            });

            // 等待完成或超时，哪个先发生
            try {
                resultFuture.get(callbackTimeout.toMillis(), TimeUnit.MILLISECONDS);
                System.out.println("回调方式正常完成。");
            } catch (TimeoutException e) {
                String timeoutMessage = "回调方式超时" + callbackTimeout.toMillis() + "ms";
                System.out.println(timeoutMessage);
            }
            
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("批处理出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 关闭批处理器
            batchJob.shutdown();
        }
    }

    /**
     * 创建批量请求
     *
     * @param count 请求数量
     * @return 请求列表
     */
    private static List<BatchRequest> createBatchRequests(int count) {
        List<BatchRequest> requests = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            BatchRequest request = BatchRequest.builder()
                    .requestId("req-" + i)
                    .modelName("gpt-3.5-turbo")
                    .prompt("解释一下Java中的并发编程概念 #" + i)
                    .maxTokens(100)
                    .temperature(0.7)
                    .build();

            requests.add(request);
        }

        return requests;
    }

    /**
     * 异步批处理示例
     */
    public static void asyncBatchJobExample() {
        // 创建配置
        BatchJobConfig config = BatchJobConfig.builder()
                .apiEndpoint("https://api.openai.com/v1/completions")
                .apiKey("your-api-key-here")
                .maxConcurrentRequests(100)
                .build();

        // 创建批处理器
        BatchJob batchJob = new BatchJob(config);
        batchJob.initialize();

        try {
            // 创建多个批次的请求
            List<BatchRequest> batch1 = createBatchRequests(50);
            List<BatchRequest> batch2 = createBatchRequests(50);
            List<BatchRequest> batch3 = createBatchRequests(50);

            // 并行处理多个批次
            CompletableFuture<BatchJobResult> future1 = batchJob.processBatch(batch1);
            CompletableFuture<BatchJobResult> future2 = batchJob.processBatch(batch2);
            CompletableFuture<BatchJobResult> future3 = batchJob.processBatch(batch3);

            // 为多个批次计算合理的超时时间（每个批次50个请求，总计150个）
            // 150个请求 * 0.5秒/请求 = 最少75秒 + 某些处理开销
            int totalRequestCount = batch1.size() + batch2.size() + batch3.size();
            long estimatedTimeout = totalRequestCount * 1000L + config.getRequestTimeout() * 6; // 基础时间 + 6倍请求超时（合理缓冲）
            long multiBatchTimeout = Math.max(config.getBatchExecutionTimeout(), estimatedTimeout);

            // 等待所有批次完成（带超时，防止无限等待）
            CompletableFuture.allOf(future1, future2, future3).get(multiBatchTimeout, TimeUnit.MILLISECONDS);

            // 处理结果
            BatchJobResult result1 = future1.get();
            BatchJobResult result2 = future2.get();
            BatchJobResult result3 = future3.get();

            System.out.println("批次1结果: " + result1.getSummary());
            System.out.println("批次2结果: " + result2.getSummary());
            System.out.println("批次3结果: " + result3.getSummary());

            // 计算总体统计信息
            int totalRequests = result1.getTotalRequests() + result2.getTotalRequests() + result3.getTotalRequests();
            int successfulRequests = result1.getSuccessfulRequests() + result2.getSuccessfulRequests() + result3.getSuccessfulRequests();
            long totalTime = result1.getProcessingTime() + result2.getProcessingTime() + result3.getProcessingTime();

            System.out.println("\n总体统计:");
            System.out.println("总请求数: " + totalRequests);
            System.out.println("成功请求数: " + successfulRequests);
            System.out.println("成功率: " + (double) successfulRequests / totalRequests * 100 + "%");
            System.out.println("总处理时间: " + totalTime + "ms");
            System.out.println("平均每请求处理时间: " + (double) totalTime / totalRequests + "ms");

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("批处理出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            batchJob.shutdown();
        }
    }

    /**
     * 流式处理示例
     */
    public static void streamingBatchJobExample() {
        // 创建配置
        BatchJobConfig config = BatchJobConfig.builder()
                .apiEndpoint("https://api.openai.com/v1/chat/completions")
                .apiKey("your-api-key-here")
                .maxConcurrentRequests(20)
                .build();

        // 创建批处理器
        BatchJob batchJob = new BatchJob(config);
        batchJob.initialize();

        try {
            // 创建批量请求
            List<BatchRequest> requests = new ArrayList<>();

            // 创建10个流式请求
            for (int i = 0; i < 10; i++) {
                BatchRequest request = BatchRequest.builder()
                        .requestId("stream-req-" + i)
                        .modelName("gpt-3.5-turbo")
                        .prompt("写一个关于人工智能的短文 #" + i)
                        .maxTokens(500)
                        .temperature(0.8)
                        .parameter("stream", true)  // 启用流式响应
                        .build();

                requests.add(request);
            }

            // 处理批量请求
            batchJob.processBatchWithCallback(requests, result -> {
                System.out.println("流式批处理完成:");
                System.out.println(result.getSummary());

                // 获取第一个响应的内容示例
                String firstResponseId = result.getResponse().getResponses().keySet().iterator().next();
                String content = result.getResponse().getResponseForRequest(firstResponseId);
                System.out.println("\n第一个响应示例:");
                System.out.println(content.substring(0, Math.min(200, content.length())) + "...");
            });

            // 计算流式处理的超时时间（基于请求数量和配置）
            long streamingTimeout = Math.max(config.getBatchExecutionTimeout(),
                    requests.size() * config.getRequestTimeout() * 2L); // 2倍时间来处理流式响应

            // 等待一定时间让流式响应完成（带超时，防止无限等待）
            Thread.sleep(Math.min(streamingTimeout, 120000)); // 最多120秒等待
            
        } catch (InterruptedException e) {
            System.err.println("批处理出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            batchJob.shutdown();
        }
    }
}