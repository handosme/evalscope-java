package com.evalscope.batchjob.netty;

import com.evalscope.batchjob.model.BatchResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 响应聚合器，负责收集和聚合来自多个请求的响应
 */
public class ResponseAggregator {
    private final Map<String, BatchResponse> responses;
    private final Map<String, AtomicInteger> pendingResponses;
    private final Map<String, AtomicInteger> expectedResponses;

    /**
     * 创建一个新的响应聚合器
     */
    public ResponseAggregator() {
        this.responses = new ConcurrentHashMap<>();
        this.pendingResponses = new ConcurrentHashMap<>();
        this.expectedResponses = new ConcurrentHashMap<>();
    }

    /**
     * 注册一个新的批次
     * 
     * @param batchId 批次ID
     * @param response 批处理响应
     * @param expectedCount 预期的响应数量
     */
    public void registerBatch(String batchId, BatchResponse response, int expectedCount) {
        responses.put(batchId, response);
        pendingResponses.put(batchId, new AtomicInteger(expectedCount));
        expectedResponses.put(batchId, new AtomicInteger(expectedCount));
    }

    /**
     * 添加响应
     * 
     * @param batchId 批次ID
     * @param requestId 请求ID
     * @param content 响应内容
     */
    public void addResponse(String batchId, String requestId, String content) {
        BatchResponse response = responses.get(batchId);
        if (response != null) {
            response.addResponse(requestId, content);
            checkBatchCompletion(batchId);
        }
    }

    /**
     * 添加错误
     * 
     * @param batchId 批次ID
     * @param requestId 请求ID
     * @param error 错误消息
     */
    public void addError(String batchId, String requestId, String error) {
        BatchResponse response = responses.get(batchId);
        if (response != null) {
            response.addError(requestId, error);
            checkBatchCompletion(batchId);
        }
    }

    /**
     * 检查批次是否完成
     * 
     * @param batchId 批次ID
     */
    private void checkBatchCompletion(String batchId) {
        AtomicInteger pending = pendingResponses.get(batchId);
        if (pending != null && pending.decrementAndGet() == 0) {
            // 所有响应都已收到，完成批处理
            BatchResponse response = responses.get(batchId);
            if (response != null) {
                response.complete();
                // 通知客户端批处理已完成
                NettyBatchClient client = getClientForBatch(batchId);
                if (client != null) {
                    client.handleResponse(batchId, response);
                }
            }
        }
    }

    /**
     * 获取批次对应的客户端
     * 
     * @param batchId 批次ID
     * @return Netty客户端
     */
    private NettyBatchClient getClientForBatch(String batchId) {
        // 在实际应用中，可能需要维护一个批次ID到客户端的映射
        // 这里简化处理，假设只有一个客户端
        return null;
    }

    /**
     * 获取批次的响应
     * 
     * @param batchId 批次ID
     * @return 批处理响应
     */
    public BatchResponse getResponse(String batchId) {
        return responses.get(batchId);
    }

    /**
     * 获取批次的完成进度
     * 
     * @param batchId 批次ID
     * @return 完成百分比（0-100）
     */
    public double getCompletionPercentage(String batchId) {
        AtomicInteger pending = pendingResponses.get(batchId);
        AtomicInteger expected = expectedResponses.get(batchId);
        
        if (pending == null || expected == null || expected.get() == 0) {
            return 0;
        }
        
        int completed = expected.get() - pending.get();
        return (double) completed / expected.get() * 100;
    }

    /**
     * 检查批次是否完成
     * 
     * @param batchId 批次ID
     * @return 如果批次完成则返回true
     */
    public boolean isBatchComplete(String batchId) {
        AtomicInteger pending = pendingResponses.get(batchId);
        return pending != null && pending.get() == 0;
    }

    /**
     * 清理批次数据
     * 
     * @param batchId 批次ID
     */
    public void cleanupBatch(String batchId) {
        responses.remove(batchId);
        pendingResponses.remove(batchId);
        expectedResponses.remove(batchId);
    }
}