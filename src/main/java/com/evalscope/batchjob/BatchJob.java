package com.evalscope.batchjob;

import com.evalscope.batchjob.model.BatchRequest;
import com.evalscope.batchjob.model.BatchResponse;
import com.evalscope.batchjob.netty.NettyBatchClient;
import com.evalscope.model.ModelResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * BatchJob用于高吞吐量地处理大批量的大模型API调用。
 * 使用Netty实现异步非阻塞IO，支持高并发请求处理。
 */
public class BatchJob {
    private final NettyBatchClient client;
    private final BatchJobConfig config;
    private final ConcurrentHashMap<String, CompletableFuture<BatchResponse>> pendingRequests;
    private final AtomicInteger requestCounter;

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
    }

    /**
     * 初始化批处理器，连接到服务器
     */
    public void initialize() {
        client.connect();
    }

    /**
     * 关闭批处理器，释放资源
     */
    public void shutdown() {
        client.shutdown();
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
        
        client.sendBatchRequest(batchId, requests);
        
        return responseFuture.thenApply(response -> {
            pendingRequests.remove(batchId);
            return new BatchJobResult(response);
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