package com.evalscope.batchjob;

import com.evalscope.batchjob.model.BatchRequest;
import com.evalscope.batchjob.model.BatchResponse;
import com.evalscope.batchjob.netty.NettyBatchClient;
import com.evalscope.model.ModelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * BatchJob用于高吞吐量地处理大批量的大模型API调用。
 * 使用Netty实现异步非阻塞IO，支持高并发请求处理。
 */
public class BatchJob {
    private static final Logger logger = LoggerFactory.getLogger(BatchJob.class);
    
    private final NettyBatchClient client;
    private final BatchJobConfig config;
    private final ConcurrentHashMap<String, CompletableFuture<BatchResponse>> pendingRequests;
    private final AtomicInteger requestCounter;
    private final ScheduledExecutorService timeoutExecutor;

    /**
     * 创建一个新的BatchJob实例
     * 
     * @param config 批处理配置
     */
    public BatchJob(BatchJobConfig config) {
        this.config = config;
        this.client = new NettyBatchClient(config);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.requestCounter = new AtomicInteger(0);
        this.timeoutExecutor = Executors.newScheduledThreadPool(1);
        
        logger.info("BatchJob实例已创建，配置: 最大并发数={}, 请求超时={}ms, 批处理超时={}ms", 
            config.getMaxConcurrentRequests(), 
            config.getRequestTimeout(),
            config.getBatchExecutionTimeout());
    }

    /**
     * 初始化批处理器，连接到服务器
     */
    public void initialize() {
        logger.info("正在初始化BatchJob，连接到服务器: {}", config.getApiEndpoint());
        client.connect();
        logger.info("BatchJob初始化完成，服务器连接已建立");
    }

    /**
     * 关闭批处理器，释放资源
     */
    public void shutdown() {
        logger.info("正在关闭BatchJob，待处理请求数: {}", getPendingRequestCount());
        client.shutdown();
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("BatchJob已关闭，资源已释放");
    }

    /**
     * 异步发送批量请求
     * 
     * @param requests 请求列表
     * @return 包含响应的CompletableFuture
     */
    public CompletableFuture<BatchJobResult> processBatch(List<BatchRequest> requests) {
        String batchId = generateBatchId();
        CompletableFuture<BatchResponse> responseFuture = new CompletableFuture<>();
        pendingRequests.put(batchId, responseFuture);
        
        long startTime = System.currentTimeMillis();
        logger.info("开始处理批次 [{}]，请求数量: {}", batchId, requests.size());
        
        // 设置超时处理
        ScheduledFuture<?> timeoutFuture = timeoutExecutor.schedule(() -> {
            if (!responseFuture.isDone()) {
                String timeoutMsg = String.format("批次 [%s] 处理超时，已等待 %dms (配置超时时间: %dms)", 
                    batchId, System.currentTimeMillis() - startTime, config.getBatchExecutionTimeout());
                logger.warn(timeoutMsg);
                TimeoutException timeoutException = new TimeoutException(timeoutMsg);
                responseFuture.completeExceptionally(timeoutException);
                pendingRequests.remove(batchId);
            }
        }, config.getBatchExecutionTimeout(), TimeUnit.MILLISECONDS);
        
        try {
            client.sendBatchRequest(batchId, requests);
            logger.debug("批次 [{}] 请求已发送到服务器", batchId);
        } catch (Exception e) {
            logger.error("发送批次 [{}] 请求时发生错误", batchId, e);
            responseFuture.completeExceptionally(e);
            pendingRequests.remove(batchId);
            timeoutFuture.cancel(false);
        }
        
        return responseFuture.thenApply(response -> {
            long processingTime = System.currentTimeMillis() - startTime;
            timeoutFuture.cancel(false);
            pendingRequests.remove(batchId);
            
            logger.info("批次 [{}] 处理完成，用时: {}ms，成功: {}，失败: {}", 
                batchId, processingTime, response.getSuccessfulRequests(), response.getFailedRequests());
            
            return new BatchJobResult(response);
        }).exceptionally(throwable -> {
            timeoutFuture.cancel(false);
            pendingRequests.remove(batchId);
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (throwable instanceof TimeoutException) {
                logger.error("批次 [{}] 处理超时，用时: {}ms", batchId, processingTime);
            } else {
                logger.error("批次 [{}] 处理失败，用时: {}ms", batchId, processingTime, throwable);
            }
            
            // 创建包含错误信息的批处理结果
            BatchResponse errorResponse = new BatchResponse();
            errorResponse.setBatchId(batchId);
            errorResponse.setStartTime(startTime);
            errorResponse.setEndTime(startTime + processingTime);
            errorResponse.setTotalRequests(requests.size());
            errorResponse.setSuccessfulRequests(0);
            errorResponse.setFailedRequests(requests.size());
            
            return new BatchJobResult(errorResponse);
        });
    }

    /**
     * 异步发送批量请求，并通过回调处理响应
     * 
     * @param requests 请求列表
     * @param callback 响应回调
     */
    public void processBatchWithCallback(List<BatchRequest> requests, Consumer<BatchJobResult> callback) {
        processBatch(requests).thenAccept(callback);
    }

    /**
     * 处理来自服务器的响应
     * 
     * @param batchId 批次ID
     * @param response 响应
     */
    void handleResponse(String batchId, BatchResponse response) {
        CompletableFuture<BatchResponse> future = pendingRequests.get(batchId);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * 处理错误
     * 
     * @param batchId 批次ID
     * @param error 错误
     */
    void handleError(String batchId, Throwable error) {
        CompletableFuture<BatchResponse> future = pendingRequests.get(batchId);
        if (future != null) {
            future.completeExceptionally(error);
        }
    }

    /**
     * 生成唯一的批次ID
     * 
     * @return 批次ID
     */
    private String generateBatchId() {
        return "batch-" + System.currentTimeMillis() + "-" + requestCounter.incrementAndGet();
    }

    /**
     * 获取当前配置
     * 
     * @return 配置
     */
    public BatchJobConfig getConfig() {
        return config;
    }

    /**
     * 获取当前待处理请求数量
     * 
     * @return 待处理请求数量
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
}